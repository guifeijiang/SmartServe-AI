package com.knowflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 RAG 检索配置（knowflow.rag）。
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {
}
