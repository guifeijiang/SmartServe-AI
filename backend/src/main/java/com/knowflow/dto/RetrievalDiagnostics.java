package com.knowflow.dto;

/**
 * 本轮检索的诊断信息：前端会展示「检索通道 / 是否降级 / 精排策略」，验收时也能直接看出走了哪条链路。
 */
public class RetrievalDiagnostics {

    /**
     * VECTOR / FULLTEXT / NONE
     */
    private String retrievalMode;
    private boolean degraded;
    /**
     * LOW_SIMILARITY / VECTOR_EMPTY / VECTOR_UNAVAILABLE，未降级时为 null。
     */
    private String degradeReason;
    /**
     * 召回候选数（精排前）。
     */
    private int recalledCount;
    /**
     * 精排后进入 Prompt 的片段数。
     */
    private int rerankedCount;
    /**
     * LLM / BM25 / NONE
     */
    private String rerankStrategy;
    /**
     * redis-stack / mysql-cosine
     */
    private String indexType;
    /**
     * ANSWERED / EMPTY_FALLBACK / HANDOFF / NO_CONTEXT
     */
    private String answerStatus;
    private String embeddingModel;

    public RetrievalDiagnostics() {
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public String getDegradeReason() {
        return degradeReason;
    }

    public void setDegradeReason(String degradeReason) {
        this.degradeReason = degradeReason;
    }

    public int getRecalledCount() {
        return recalledCount;
    }

    public void setRecalledCount(int recalledCount) {
        this.recalledCount = recalledCount;
    }

    public int getRerankedCount() {
        return rerankedCount;
    }

    public void setRerankedCount(int rerankedCount) {
        this.rerankedCount = rerankedCount;
    }

    public String getRerankStrategy() {
        return rerankStrategy;
    }

    public void setRerankStrategy(String rerankStrategy) {
        this.rerankStrategy = rerankStrategy;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public String getAnswerStatus() {
        return answerStatus;
    }

    public void setAnswerStatus(String answerStatus) {
        this.answerStatus = answerStatus;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
