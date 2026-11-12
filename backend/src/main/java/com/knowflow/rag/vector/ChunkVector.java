package com.knowflow.rag.vector;

/**
 * 待写入向量索引的分块：MySQL 中的分块元数据 + 向量本身。
 */
public class ChunkVector {

    private final Long chunkId;
    private final Long knowledgeBaseId;
    private final Long documentId;
    private final Integer chunkIndex;
    private final String embeddingModel;
    private final String content;
    private final float[] vector;

    public ChunkVector(
            Long chunkId,
            Long knowledgeBaseId,
            Long documentId,
            Integer chunkIndex,
            String embeddingModel,
            String content,
            float[] vector
    ) {
        this.chunkId = chunkId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.embeddingModel = embeddingModel;
        this.content = content;
        this.vector = vector;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public String getContent() {
        return content;
    }

    public float[] getVector() {
        return vector;
    }
}
