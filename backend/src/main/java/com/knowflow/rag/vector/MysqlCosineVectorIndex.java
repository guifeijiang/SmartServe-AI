package com.knowflow.rag.vector;

import com.knowflow.entity.RetrievableChunk;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.rag.CosineSimilarity;
import com.knowflow.service.EmbeddingService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Redis Stack 不可用时的降级索引：向量存放在 MySQL document_chunk.embedding_json，
 * 检索在应用侧计算余弦相似度。功能等价但复杂度 O(N)，因此必须明确标注 type 以便验收区分。
 */
public class MysqlCosineVectorIndex implements VectorIndex {

    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingService embeddingService;

    public MysqlCosineVectorIndex(DocumentChunkMapper documentChunkMapper, EmbeddingService embeddingService) {
        this.documentChunkMapper = documentChunkMapper;
        this.embeddingService = embeddingService;
    }

    @Override
    public String type() {
        return "mysql-cosine";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<VectorHit> search(Long knowledgeBaseId, String embeddingModel, float[] queryVector, int topK) {
        List<RetrievableChunk> candidates = documentChunkMapper.findReadyByKnowledgeBaseId(knowledgeBaseId);
        List<VectorHit> hits = new ArrayList<>();
        for (RetrievableChunk chunk : candidates) {
            if (embeddingModel != null && chunk.getEmbeddingModel() != null
                    && !embeddingModel.equals(chunk.getEmbeddingModel())) {
                continue;
            }
            float[] vector = embeddingService.fromJson(chunk.getEmbeddingJson());
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double score = CosineSimilarity.score(queryVector, vector);
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                continue;
            }
            hits.add(new VectorHit(chunk.getId(), score, type()));
        }
        hits.sort(Comparator.comparingDouble(VectorHit::getSimilarity).reversed());
        return hits.size() <= topK ? hits : new ArrayList<>(hits.subList(0, topK));
    }

    @Override
    public void upsert(List<ChunkVector> chunks) {
        // 向量已经由 DocumentService 写入 MySQL，这里无需重复写。
    }

    @Override
    public void deleteByDocument(Long documentId) {
        // 分块随 document_chunk 一起删除，无需额外清理。
    }
}
