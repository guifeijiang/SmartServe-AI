package com.knowflow.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * 选择 Spring AI Alibaba 内置 text-embedding-v3 的条件：
 * provider=dashscope 强制启用；provider=auto 且存在 DASHSCOPE_API_KEY 时启用。
 */
public class DashScopeEmbeddingEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = normalize(context.getEnvironment().getProperty("knowflow.ai.embedding.provider"));
        if ("none".equals(provider)) {
            return false;
        }
        if (!"dashscope".equals(provider) && !"auto".equals(provider)) {
            return false;
        }
        String key = context.getEnvironment().getProperty("knowflow.ai.embedding.dashscope.api-key");
        return StringUtils.hasText(key) && !"placeholder".equalsIgnoreCase(key.trim());
    }

    static String normalize(String provider) {
        return StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "auto";
    }
}
