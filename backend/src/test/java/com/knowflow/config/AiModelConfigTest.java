package com.knowflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.DeepSeekChatModel;
import com.knowflow.ai.OllamaChatModel;
import com.knowflow.ai.OllamaEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiModelConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AiModelConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void createsOllamaChatAndOllamaEmbeddingByDefault() {
        runner.withPropertyValues(
                "knowflow.ai.chat.provider=ollama",
                "knowflow.ai.chat.ollama.base-url=http://127.0.0.1:11434",
                "knowflow.ai.chat.ollama.model=deepseek-r1:1.5b",
                "knowflow.ai.embedding.provider=ollama",
                "knowflow.ai.embedding.base-url=http://127.0.0.1:11434",
                "knowflow.ai.embedding.model=bge-m3"
        ).run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(OllamaChatModel.class);
            assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
        });
    }

    @Test
    void createsDeepSeekChatWhenConfigured() {
        runner.withPropertyValues(
                "knowflow.ai.chat.provider=deepseek",
                "knowflow.ai.chat.deepseek.api-key=test-key",
                "knowflow.ai.chat.deepseek.base-url=https://api.deepseek.com",
                "knowflow.ai.chat.deepseek.model=deepseek-chat",
                "knowflow.ai.embedding.provider=ollama"
        ).run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(DeepSeekChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
        });
    }

    @Test
    void skipsDeepSeekChatWhenApiKeyIsMissing() {
        runner.withPropertyValues(
                "knowflow.ai.chat.provider=deepseek",
                "knowflow.ai.chat.deepseek.api-key=",
                "knowflow.ai.embedding.provider=ollama"
        ).run(context -> {
            assertThat(context).doesNotHaveBean(ChatModel.class);
            assertThat(context).hasSingleBean(EmbeddingModel.class);
        });
    }

    @Test
    void createsDashScopeEmbeddingWhenProviderAndKeyConfigured() {
        runner.withPropertyValues(
                "knowflow.ai.chat.provider=none",
                "knowflow.ai.embedding.provider=dashscope",
                "knowflow.ai.embedding.dashscope.api-key=test-key",
                "knowflow.ai.embedding.dashscope.model=text-embedding-v3",
                "knowflow.ai.embedding.dashscope.dimensions=1024"
        ).run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context.getBean(EmbeddingModel.class))
                    .isInstanceOf(com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel.class);
        });
    }

    @Test
    void skipsDashScopeEmbeddingWhenApiKeyMissing() {
        runner.withPropertyValues(
                "knowflow.ai.chat.provider=none",
                "knowflow.ai.embedding.provider=dashscope",
                "knowflow.ai.embedding.dashscope.api-key="
        ).run(context -> assertThat(context).doesNotHaveBean(EmbeddingModel.class));
    }

    @Test
    void autoProviderUsesDashScopeWhenApiKeyPresent() {
        runner.withUserConfiguration(com.knowflow.service.EmbeddingService.class)
                .withPropertyValues(
                        "knowflow.ai.chat.provider=none",
                        "knowflow.ai.embedding.provider=auto",
                        "knowflow.ai.embedding.dashscope.api-key=test-key",
                        "knowflow.ai.embedding.dashscope.model=text-embedding-v3",
                        "knowflow.ai.embedding.dashscope.dimensions=1024"
                ).run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(EmbeddingModel.class))
                            .isInstanceOf(com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel.class);
                    assertThat(context.getBean(com.knowflow.service.EmbeddingService.class).modelName())
                            .isEqualTo("text-embedding-v3");
                    assertThat(context.getBean(com.knowflow.service.EmbeddingService.class).dimensions())
                            .isEqualTo(1024);
                });
    }

    @Test
    void autoProviderFallsBackToOllamaWhenApiKeyMissing() {
        runner.withUserConfiguration(com.knowflow.service.EmbeddingService.class)
                .withPropertyValues(
                        "knowflow.ai.chat.provider=none",
                        "knowflow.ai.embedding.provider=auto",
                        "knowflow.ai.embedding.dashscope.api-key=",
                        "knowflow.ai.embedding.model=bge-m3",
                        "knowflow.ai.embedding.dimensions=1024"
                ).run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingModel.class);
                    assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(OllamaEmbeddingModel.class);
                    assertThat(context.getBean(com.knowflow.service.EmbeddingService.class).modelName())
                            .isEqualTo("bge-m3");
                });
    }

    @Test
    void skipsProvidersWhenDisabled() {
        runner.withPropertyValues(
                "knowflow.ai.chat.provider=none",
                "knowflow.ai.embedding.provider=none"
        ).run(context -> {
            assertThat(context).doesNotHaveBean(ChatModel.class);
            assertThat(context).doesNotHaveBean(EmbeddingModel.class);
        });
    }
}
