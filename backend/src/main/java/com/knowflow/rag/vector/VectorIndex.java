package com.knowflow.rag.vector;

import java.util.List;

/**
 * 向量索引抽象。两种实现：
 * 1) RedisStackVectorIndex：Redis Stack + RediSearch HNSW 真正的向量检索；
 * 2) MysqlCosineVectorIndex：Redis Stack 不可用时的降级索引（分块向量存 MySQL，Java 侧算余弦）。
 */
public interface VectorIndex {

    String type();

    boolean available();

    List<VectorHit> search(Long knowledgeBaseId, String embeddingModel, float[] queryVector, int topK);

    void upsert(List<ChunkVector> chunks);

    void deleteByDocument(Long documentId);

    /**
     * 清空某个知识库在索引里的全部向量，用于重建索引前先清理，避免 MySQL 已删除的分块残留在 Redis。
     * 默认空实现：MySQL 余弦索引的向量随 document_chunk 一起删除，无需单独清理。
     */
    default void deleteByKnowledgeBase(Long knowledgeBaseId) {
        // no-op
    }

    /**
     * 统计某个知识库在索引中的向量条数，用于重建后校验 Redis 与 MySQL 的分块数是否一致。
     * 默认返回 0：MySQL 余弦索引的权威数据就是 document_chunk，不需要额外计数。
     */
    default int countByKnowledgeBase(Long knowledgeBaseId) {
        return 0;
    }
}
