package com.knowflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI provider configuration. Keys are read from environment variables and never hard-coded.
 */
@ConfigurationProperties(prefix = "knowflow.ai")
public class AiProperties {

    private final Chat chat = new Chat();
    private final Embedding embedding = new Embedding();

    public Chat getChat() {
        return chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public static class Chat {

        /**
         * "ollama" (local, default) or "deepseek" (remote API, optional).
         */
        private String provider = "ollama";
        private final Ollama ollama = new Ollama();
        private final DeepSeek deepseek = new DeepSeek();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public Ollama getOllama() {
            return ollama;
        }

        public DeepSeek getDeepseek() {
            return deepseek;
        }
    }

    public static class Ollama {

        private String baseUrl = "http://127.0.0.1:11434";
        private String model = "deepseek-r1:1.5b";
        private double temperature = 0.0;
        private int timeoutMs = 300000;
        private int numCtx = 8192;
        private int numPredict = 2048;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getNumCtx() {
            return numCtx;
        }

        public void setNumCtx(int numCtx) {
            this.numCtx = numCtx;
        }

        public int getNumPredict() {
            return numPredict;
        }

        public void setNumPredict(int numPredict) {
            this.numPredict = numPredict;
        }
    }

    public static class DeepSeek {

        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private double temperature = 0.2;
        private int timeoutMs = 60000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Embedding {

        /**
         * "ollama"（本地 bge-m3，默认）或 "dashscope"（百炼 text-embedding-v3）。
         */
        private String provider = "ollama";
        private String baseUrl = "http://127.0.0.1:11434";
        private String model = "bge-m3";
        private int dimensions = 1024;
        private int timeoutMs = 60000;
        private final DashScope dashscope = new DashScope();

        public DashScope getDashscope() {
            return dashscope;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public static class DashScope {

            private String baseUrl = "https://dashscope.aliyuncs.com";
            private String apiKey = "";
            private String model = "text-embedding-v3";
            private int dimensions = 1024;
            private int timeoutMs = 30000;

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }

            public int getDimensions() {
                return dimensions;
            }

            public void setDimensions(int dimensions) {
                this.dimensions = dimensions;
            }

            public int getTimeoutMs() {
                return timeoutMs;
            }

            public void setTimeoutMs(int timeoutMs) {
                this.timeoutMs = timeoutMs;
            }
        }
    }
}
