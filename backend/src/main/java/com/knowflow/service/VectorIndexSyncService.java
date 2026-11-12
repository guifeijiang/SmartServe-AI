package com.knowflow.service;

import com.knowflow.entity.RetrievableChunk;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.rag.vector.ChunkVector;
import com.knowflow.rag.vector.VectorIndex;
import com.knowflow.rag.vector.VectorIndexRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 MySQL 中的分块向量同步到向量索引（Redis Stack），或从已有向量重建索引。
 * 同步失败只记录日志，不影响文档处理主流程：MySQL 始终保留向量，可随时重建。
 */
@Service
public class VectorIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexSyncService.class);

    /**
     * 重建时单批写入的条数：单批失败可以立即停止并整体判失败，避免一次请求写几千条时难以定位。
     */
    private static final int WRITE_BATCH_SIZE = 500;

    private final VectorIndexRouter vectorIndexRouter;
    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingService embeddingService;

    public VectorIndexSyncService(
            VectorIndexRouter vectorIndexRouter,
            DocumentChunkMapper documentChunkMapper,
            EmbeddingService embeddingService
    ) {
        this.vectorIndexRouter = vectorIndexRouter;
        this.documentChunkMapper = documentChunkMapper;
        this.embeddingService = embeddingService;
    }

    public void syncDocument(Long documentId) {
        VectorIndex index = vectorIndexRouter.active();
        if (!"redis-stack".equals(index.type())) {
            // MySQL 余弦索引直接读 document_chunk.embedding_json，向量已随事务落库，无需二次同步。
            documentChunkMapper.markVectorIndexState(documentId, "SYNCED");
            return;
        }
        try {
            List<RetrievableChunk> chunks = documentChunkMapper.findReadyByDocumentId(documentId);
            // 先删旧条目再写入：文档重新解析后 chunk_id 会变，不清理会留下永远召不回的孤儿向量。
            index.deleteByDocument(documentId);
            index.upsert(toVectors(chunks));
            documentChunkMapper.markVectorIndexState(documentId, "SYNCED");
        } catch (RuntimeException ex) {
            documentChunkMapper.markVectorIndexState(documentId, "FAILED");
            log.error("向量索引同步失败，documentId={} 已标记 FAILED：{}", documentId, ex.getMessage(), ex);
        }
    }

    /**
     * 文档删除时清理向量索引中的对应条目，避免召回已不存在的分块。
     */
    public void removeDocument(Long documentId) {
        try {
            vectorIndexRouter.active().deleteByDocument(documentId);
        } catch (RuntimeException ex) {
            // 删文档时清理向量失败不能拖垮 MySQL 侧删除，但必须留下明确错误日志，便于后续重建修掉残留。
            log.error("清理向量索引失败，documentId={} 可能存在残留向量：{}", documentId, ex.getMessage(), ex);
        }
    }

    /**
     * 用 MySQL 已存向量重建整个知识库的向量索引，不重新调用 Embedding 接口，因此不产生调用费用。
     *
     * <p>重建是「先清空、再分批写入、最后核对条数」的三步：只有三步全部成功才把文档标成 SYNCED；
     * 任意一步抛异常都会把该知识库下的文档统一标成 FAILED，并把异常抛给调用方，绝不因为「写进去一部分」
     * 就返回成功。整个过程以「清空 + HSET 覆盖」实现，重复执行结果一致，因此重试是幂等的。
     * MySQL 的分块元数据全程只读，不会被重建过程改动。</p>
     */
    public int rebuild(Long knowledgeBaseId) {
        VectorIndex index = vectorIndexRouter.active();
        List<RetrievableChunk> chunks = documentChunkMapper.findReadyByKnowledgeBaseId(knowledgeBaseId);
        List<Long> documentIds = chunks.stream()
                .map(RetrievableChunk::getDocumentId)
                .distinct()
                .toList();
        if (!"redis-stack".equals(index.type())) {
            // 降级索引下向量只存在于 MySQL，重建等价于「已可用」，仍然回报可用分块数，避免前端误以为失败。
            log.info("当前索引为 {}，知识库 {} 的 {} 条向量直接来自 MySQL，无需写入 Redis",
                    index.type(), knowledgeBaseId, chunks.size());
            markState(documentIds, "SYNCED");
            return chunks.size();
        }
        try {
            // 1) 清空该知识库的旧向量（分批删到查不到为止），保证不会出现「新旧混在一起」。
            index.deleteByKnowledgeBase(knowledgeBaseId);
            // 2) 分批写入：单批失败时异常会在这里抛出，后面的批次不再继续。
            List<ChunkVector> vectors = toVectors(chunks);
            for (int from = 0; from < vectors.size(); from += WRITE_BATCH_SIZE) {
                int to = Math.min(from + WRITE_BATCH_SIZE, vectors.size());
                index.upsert(vectors.subList(from, to));
            }
            // 3) 核对 Redis 里的条数与 MySQL 分块数：不一致说明写入不完整，按失败处理。
            int stored = index.countByKnowledgeBase(knowledgeBaseId);
            if (stored != vectors.size()) {
                throw new IllegalStateException("Redis 向量条数 " + stored + " 与 MySQL 可检索分块数 "
                        + vectors.size() + " 不一致，重建未完成");
            }
            markState(documentIds, "SYNCED");
            log.info("知识库 {} 向量索引重建完成，共 {} 条", knowledgeBaseId, vectors.size());
            return vectors.size();
        } catch (RuntimeException ex) {
            markState(documentIds, "FAILED");
            log.error("知识库 {} 向量重建失败，已将该知识库文档标记为 FAILED：{}", knowledgeBaseId, ex.getMessage(), ex);
            throw new IllegalStateException("知识库 " + knowledgeBaseId + " 向量重建失败：" + ex.getMessage(), ex);
        }
    }

    private void markState(List<Long> documentIds, String state) {
        documentIds.forEach(documentId -> documentChunkMapper.markVectorIndexState(documentId, state));
    }

    private List<ChunkVector> toVectors(List<RetrievableChunk> chunks) {
        List<ChunkVector> vectors = new ArrayList<>(chunks.size());
        for (RetrievableChunk chunk : chunks) {
            float[] vector = embeddingService.fromJson(chunk.getEmbeddingJson());
            if (vector == null || vector.length != embeddingService.dimensions()) {
                log.warn("跳过维度不匹配的分块 {}（期望 {} 维）", chunk.getId(), embeddingService.dimensions());
                continue;
            }
            vectors.add(new ChunkVector(
                    chunk.getId(),
                    chunk.getKnowledgeBaseId(),
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    chunk.getEmbeddingModel() == null ? embeddingService.modelName() : chunk.getEmbeddingModel(),
                    chunk.getContent(),
                    vector
            ));
        }
        return vectors;
    }
}
