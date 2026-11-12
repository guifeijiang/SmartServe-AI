package com.knowflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索链路配置：向量索引、召回条数、精排条数、相似度阈值与降级通道。
 */
@ConfigurationProperties(prefix = "knowflow.rag")
public class RagProperties {

    /**
     * 向量索引实现：auto（优先 Redis Stack，不可用时回退 MySQL 余弦）、redis、mysql。
     */
    private String vectorStore = "auto";
    /**
     * 向量召回条数（Top-K）。
     */
    private int recallTopK = 5;
    /**
     * 精排后进入 Prompt 的片段数。
     */
    private int rerankTopN = 3;
    /**
     * 向量召回的最低余弦相似度；低于该值时降级到 MySQL 全文检索。
     */
    private double similarityThreshold = 0.45;
    /**
     * 精排策略：auto（优先模型精排，失败回退 BM25）、llm、bm25。
     */
    private String rerankStrategy = "auto";
    /**
     * 注入 Prompt 的知识库原文总字数上限（Top-N 片段累计），用于控制上下文长度与 token 成本。
     */
    private int contextMaxChars = 1500;

    private final Redis redis = new Redis();

    public String getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(String vectorStore) {
        this.vectorStore = vectorStore;
    }

    public int getRecallTopK() {
        return recallTopK;
    }

    public void setRecallTopK(int recallTopK) {
        this.recallTopK = recallTopK;
    }

    public int getRerankTopN() {
        return rerankTopN;
    }

    public void setRerankTopN(int rerankTopN) {
        this.rerankTopN = rerankTopN;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public String getRerankStrategy() {
        return rerankStrategy;
    }

    public void setRerankStrategy(String rerankStrategy) {
        this.rerankStrategy = rerankStrategy;
    }

    public int getContextMaxChars() {
        return contextMaxChars;
    }

    public void setContextMaxChars(int contextMaxChars) {
        this.contextMaxChars = contextMaxChars;
    }

    public Redis getRedis() {
        return redis;
    }

    public static class Redis {

        private String host = "127.0.0.1";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int timeoutMs = 3000;
        private String indexName = "knowflow:chunk:idx";
        private String keyPrefix = "knowflow:chunk:";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
