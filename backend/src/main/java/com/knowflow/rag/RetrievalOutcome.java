package com.knowflow.rag;

import java.util.List;

/**
 * 一次检索的完整结果与诊断信息：走的是向量还是全文降级、是否触发降级、用了哪种精排。
 */
public class RetrievalOutcome {

    public static final String MODE_VECTOR = "VECTOR";
    public static final String MODE_FULLTEXT = "FULLTEXT";
    public static final String MODE_NONE = "NONE";

    private final String retrievalMode;
    private final boolean degraded;
    private final String degradeReason;
    private final int recalledCount;
    private final String indexType;
    private final String rerankStrategy;
    private final List<RetrievedChunk> chunks;

    public RetrievalOutcome(
            String retrievalMode,
            boolean degraded,
            String degradeReason,
            int recalledCount,
            String indexType,
            String rerankStrategy,
            List<RetrievedChunk> chunks
    ) {
        this.retrievalMode = retrievalMode;
        this.degraded = degraded;
        this.degradeReason = degradeReason;
        this.recalledCount = recalledCount;
        this.indexType = indexType;
        this.rerankStrategy = rerankStrategy;
        this.chunks = chunks;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String getDegradeReason() {
        return degradeReason;
    }

    public int getRecalledCount() {
        return recalledCount;
    }

    public String getIndexType() {
        return indexType;
    }

    public String getRerankStrategy() {
        return rerankStrategy;
    }

    public List<RetrievedChunk> getChunks() {
        return chunks;
    }
}
