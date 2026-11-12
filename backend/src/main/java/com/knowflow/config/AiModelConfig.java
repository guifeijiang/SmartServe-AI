package com.knowflow.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.DashScopeEmbeddingEnabledCondition;
import com.knowflow.ai.DeepSeekApiKeyPresentCondition;
import com.knowflow.ai.DeepSeekChatModel;
import com.knowflow.ai.OllamaChatModel;
import com.knowflow.ai.OllamaEmbeddingEnabledCondition;
import com.knowflow.ai.OllamaEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * ChatModel：本地 Ollama（默认，DeepSeek-R1 1.5B）或远程 DeepSeek API。
 * EmbeddingModel：provider=auto 时，有 DASHSCOPE_API_KEY 就用 Spring AI Alibaba 内置 text-embedding-v3，
 * 否则回退本地 Ollama BGE-M3，保证离线也能跑。
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiModelConfig {

    @Bean
    @ConditionalOnProperty(prefix = "knowflow.ai.chat", name = "provider", havingValue = "ollama", matchIfMissing = true)
    public ChatModel ollamaChatModel(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Ollama chat = properties.getChat().getOllama();
        RestClient restClient = RestClient.builder()
                .baseUrl(chat.getBaseUrl())
                .requestFactory(requestFactory(chat.getTimeoutMs()))
                .build();
        return new OllamaChatModel(
                restClient,
                objectMapper,
                chat.getModel(),
                chat.getTemperature(),
                chat.getNumCtx(),
                chat.getNumPredict()
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "knowflow.ai.chat", name = "provider", havingValue = "deepseek")
    @Conditional(DeepSeekApiKeyPresentCondition.class)
    public ChatModel deepSeekChatModel(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.DeepSeek chat = properties.getChat().getDeepseek();
        RestClient restClient = RestClient.builder()
                .baseUrl(chat.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + chat.getApiKey())
                .requestFactory(requestFactory(chat.getTimeoutMs()))
                .build();
        return new DeepSeekChatModel(restClient, objectMapper, chat.getModel(), chat.getTemperature());
    }

    @Bean
    @Conditional(OllamaEmbeddingEnabledCondition.class)
    public EmbeddingModel ollamaEmbeddingModel(AiProperties properties, ObjectMapper objectMapper) {
        AiProperties.Embedding embedding = properties.getEmbedding();
        RestClient restClient = RestClient.builder()
                .baseUrl(embedding.getBaseUrl())
                .requestFactory(requestFactory(embedding.getTimeoutMs()))
                .build();
        return new OllamaEmbeddingModel(
                restClient,
                objectMapper,
                embedding.getModel(),
                embedding.getDimensions()
        );
    }

    /**
     * Spring AI Alibaba 内置 DashScope 向量模型：text-embedding-v3。
     * 仅在 provider=dashscope 且配置了 API Key 时创建；走 DashScope 原生 /services/embeddings 接口。
     */
    @Bean
    @Conditional(DashScopeEmbeddingEnabledCondition.class)
    public EmbeddingModel dashScopeEmbeddingModel(AiProperties properties) {
        AiProperties.Embedding.DashScope dashscope = properties.getEmbedding().getDashscope();
        DashScopeApi api = DashScopeApi.builder()
                .baseUrl(dashscope.getBaseUrl())
                .apiKey(dashscope.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory(dashscope.getTimeoutMs())))
                .build();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel(dashscope.getModel())
                .withDimensions(dashscope.getDimensions())
                .build();
        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(api)
                .metadataMode(MetadataMode.NONE)
                .defaultOptions(options)
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(int timeoutMs) {        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return factory;
    }
}
