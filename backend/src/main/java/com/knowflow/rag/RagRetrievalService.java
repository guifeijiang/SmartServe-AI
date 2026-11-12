package com.knowflow.rag;

import com.knowflow.config.RagProperties;
import com.knowflow.entity.RetrievableChunk;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.rag.vector.VectorHit;
import com.knowflow.rag.vector.VectorIndex;
import com.knowflow.rag.vector.VectorIndexRouter;
import com.knowflow.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索编排：向量召回 Top-K → 相似度阈值判定 → 全文检索降级 → 精排 → Top-N。
 *
 * 三级降级：
 * 1) 向量层不可用（Embedding 失败 / 索引异常 / 索引为空）→ MySQL FULLTEXT；
 * 2) 向量相似度低于阈值 → MySQL FULLTEXT；
 * 3) 全文检索仍为空 → 调用方返回固定兜底话术（知识库无相关内容）。
 */
@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);

    private final EmbeddingService embeddingService;
    private final VectorIndexRouter vectorIndexRouter;
    private final DocumentChunkMapper documentChunkMapper;
    private final RerankService rerankService;
    private final RagProperties ragProperties;

    public RagRetrievalService(
            EmbeddingService embeddingService,
            VectorIndexRouter vectorIndexRouter,
            DocumentChunkMapper documentChunkMapper,
            RerankService rerankService,
            RagProperties ragProperties
    ) {
        this.embeddingService = embeddingService;
        this.vectorIndexRouter = vectorIndexRouter;
        this.documentChunkMapper = documentChunkMapper;
        this.rerankService = rerankService;
        this.ragProperties = ragProperties;
    }

    public RetrievalOutcome retrieve(Long knowledgeBaseId, String question) {
        int topK = ragProperties.getRecallTopK();
        String indexType = vectorIndexRouter.type();

        List<RetrievedChunk> candidates;
        String degradeReason = null;
        boolean degraded = false;
        try {
            candidates = vectorRecall(knowledgeBaseId, question, topK);
            if (candidates.isEmpty()) {
                degraded = true;
                degradeReason = "VECTOR_EMPTY";
                candidates = fulltext(knowledgeBaseId, question);
            } else if (topScore(candidates) < ragProperties.getSimilarityThreshold()) {
                degraded = true;
                degradeReason = "LOW_SIMILARITY";
                candidates = fulltext(knowledgeBaseId, question);
            }
        } catch (RuntimeException ex) {
            degraded = true;
            degradeReason = "VECTOR_UNAVAILABLE";
            log.warn("向量召回失败，降级 MySQL 全文检索: {}", ex.getMessage());
            candidates = fulltext(knowledgeBaseId, question);
        }

        if (candidates.isEmpty()) {
            return new RetrievalOutcome(
                    RetrievalOutcome.MODE_NONE, degraded, degradeReason, 0, indexType,
                    RerankOutcome.STRATEGY_NONE, List.of()
            );
        }

        RerankOutcome reranked = rerankService.rerank(question, candidates, ragProperties.getRerankTopN());
        String mode = RetrievalOutcome.MODE_VECTOR;
        if (degraded && degradeReason != null) {
            // 降级后若拿到全文结果，模式记为 FULLTEXT，便于前端和验收区分通道。
            mode = RetrievalOutcome.MODE_FULLTEXT;
        }
        return new RetrievalOutcome(
                mode,
                degraded,
                degradeReason,
                candidates.size(),
                indexType,
                reranked.getStrategy(),
                reranked.getChunks()
        );
    }

    private List<RetrievedChunk> vectorRecall(Long knowledgeBaseId, String question, int topK) {
        float[] queryVector = embeddingService.embed(question);
        VectorIndex index = vectorIndexRouter.active();
        List<VectorHit> hits = index.search(knowledgeBaseId, embeddingService.modelName(), queryVector, topK);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<Long> ids = hits.stream().map(VectorHit::getChunkId).toList();
        Map<Long, RetrievableChunk> byId = byId(documentChunkMapper.findByIds(ids));
        List<RetrievedChunk> chunks = new ArrayList<>(hits.size());
        for (VectorHit hit : hits) {
            RetrievableChunk chunk = byId.get(hit.getChunkId());
            if (chunk == null) {
                // 向量索引里的分块可能已被删除，跳过。
                continue;
            }
            chunks.add(toRetrieved(chunk, hit.getSimilarity()));
        }
        return chunks;
    }

    private List<RetrievedChunk> fulltext(Long knowledgeBaseId, String question) {
        List<RetrievableChunk> rows = documentChunkMapper.searchFulltext(
                knowledgeBaseId,
                question,
                embeddingService.modelName(),
                ragProperties.getRecallTopK()
        );
        List<RetrievedChunk> chunks = new ArrayList<>(rows.size());
        for (RetrievableChunk row : rows) {
            chunks.add(toRetrieved(row, null));
        }
        return chunks;
    }

    private static Map<Long, RetrievableChunk> byId(List<RetrievableChunk> rows) {
        Map<Long, RetrievableChunk> map = new LinkedHashMap<>();
        for (RetrievableChunk row : rows) {
            map.put(row.getId(), row);
        }
        return map;
    }

    private static RetrievedChunk toRetrieved(RetrievableChunk chunk, Double vectorScore) {
        return new RetrievedChunk(
                chunk.getId(),
                chunk.getDocumentId(),
                chunk.getOriginalFilename(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                vectorScore
        );
    }

    private static double topScore(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(RetrievedChunk::getVectorScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }
}
