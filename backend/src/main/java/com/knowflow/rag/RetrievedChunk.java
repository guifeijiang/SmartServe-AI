package com.knowflow.rag;

/**
 * 检索链路中的一条候选片段：带向量召回分与精排分，两个分数分开保留便于验收。
 */
public class RetrievedChunk {

    private final Long chunkId;
    private final Long documentId;
    private final String documentName;
    private final Integer chunkIndex;
    private final String content;
    private final Double vectorScore;
    private Double rerankScore;

    public RetrievedChunk(
            Long chunkId,
            Long documentId,
            String documentName,
            Integer chunkIndex,
            String content,
            Double vectorScore
    ) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.vectorScore = vectorScore;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public Double getVectorScore() {
        return vectorScore;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
    }
}
