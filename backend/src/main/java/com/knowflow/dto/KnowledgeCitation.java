package com.knowflow.dto;

public class KnowledgeCitation {

    private Long chunkId;
    private Long documentId;
    private String documentName;
    private Integer chunkIndex;
    private String content;
    /**
     * 最终用于排序的相关度：精排分优先，未精排时用向量相似度。
     */
    private Double score;
    private Double vectorScore;
    private Double rerankScore;

    public KnowledgeCitation() {
    }

    public KnowledgeCitation(Long documentId, String documentName, Integer chunkIndex, String content, Double score) {
        this(null, documentId, documentName, chunkIndex, content, score);
    }

    public KnowledgeCitation(
            Long chunkId,
            Long documentId,
            String documentName,
            Integer chunkIndex,
            String content,
            Double score
    ) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.score = score;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(Double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public Double getRerankScore() {
        return rerankScore;
    }

    public void setRerankScore(Double rerankScore) {
        this.rerankScore = rerankScore;
    }
}
