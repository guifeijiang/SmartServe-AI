package com.knowflow.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 选择本地 Ollama BGE-M3 的条件：
 * provider=ollama 强制启用；provider=auto 且没有 DASHSCOPE_API_KEY 时回退到本地模型，保证离线可跑。
 */
public class OllamaEmbeddingEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = DashScopeEmbeddingEnabledCondition.normalize(
                context.getEnvironment().getProperty("knowflow.ai.embedding.provider"));
        if ("none".equals(provider) || "dashscope".equals(provider)) {
            return false;
        }
        if ("ollama".equals(provider)) {
            return true;
        }
        String key = context.getEnvironment().getProperty("knowflow.ai.embedding.dashscope.api-key");
        return !StringUtils.hasText(key) || "placeholder".equalsIgnoreCase(key.trim());
    }
}
