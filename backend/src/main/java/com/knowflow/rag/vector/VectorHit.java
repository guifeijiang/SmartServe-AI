package com.knowflow.rag.vector;

/**
 * 一次向量检索命中的分块。similarity 为余弦相似度（Redis COSINE 距离已换算）。
 */
public class VectorHit {

    private final Long chunkId;
    private final double similarity;
    private final String source;

    public VectorHit(Long chunkId, double similarity, String source) {
        this.chunkId = chunkId;
        this.similarity = similarity;
        this.source = source;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public double getSimilarity() {
        return similarity;
    }

    public String getSource() {
        return source;
    }
}
