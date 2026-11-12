package com.knowflow.rag;

import java.util.List;

/**
 * 精排结果：strategy 记录实际生效的策略（LLM / BM25 / NONE），不允许把余弦排序伪装成精排。
 */
public class RerankOutcome {

    public static final String STRATEGY_LLM = "LLM";
    public static final String STRATEGY_BM25 = "BM25";
    public static final String STRATEGY_NONE = "NONE";

    private final String strategy;
    private final List<RetrievedChunk> chunks;

    public RerankOutcome(String strategy, List<RetrievedChunk> chunks) {
        this.strategy = strategy;
        this.chunks = chunks;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<RetrievedChunk> getChunks() {
        return chunks;
    }
}
