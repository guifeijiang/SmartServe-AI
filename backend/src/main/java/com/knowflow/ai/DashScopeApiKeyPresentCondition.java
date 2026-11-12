package com.knowflow.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * text-embedding-v3 只在配置了 DashScope API Key 时才创建，避免启动即申请付费接口。
 */
public class DashScopeApiKeyPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String key = context.getEnvironment().getProperty("knowflow.ai.embedding.dashscope.api-key");
        return StringUtils.hasText(key) && !"placeholder".equalsIgnoreCase(key.trim());
    }
}
