package com.knowflow.ai;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Creates the DeepSeek ChatModel only when a non-blank API key is configured.
 */
public class DeepSeekApiKeyPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return StringUtils.hasText(context.getEnvironment().getProperty("knowflow.ai.chat.deepseek.api-key"));
    }
}
