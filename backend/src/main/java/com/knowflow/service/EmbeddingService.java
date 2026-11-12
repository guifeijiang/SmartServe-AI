package com.knowflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.AiServiceException;
import com.knowflow.config.AiProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final String modelName;
    private final int dimensions;

    @Autowired
    public EmbeddingService(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectMapper objectMapper,
            AiProperties aiProperties
    ) {
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        AiProperties.Embedding embedding = aiProperties.getEmbedding();
        // provider=auto 时 Bean 可能是 Spring AI Alibaba 的 text-embedding-v3，也可能是本地 bge-m3，
        // 以实际装配的 Bean 类型为准，避免诊断里把模型名报错。
        boolean dashScopeModel = embeddingModel
                instanceof com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
        if (dashScopeModel || "dashscope".equalsIgnoreCase(embedding.getProvider())) {
            this.modelName = embedding.getDashscope().getModel();
            this.dimensions = embedding.getDashscope().getDimensions();
        } else {
            this.modelName = embedding.getModel();
            this.dimensions = embedding.getDimensions();
        }
    }

    /**
     * 仅用于单元测试与适配器测试：直接指定模型名和维度。
     */
    public EmbeddingService(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectMapper objectMapper,
            String modelName
    ) {
        this(embeddingModelProvider, objectMapper, modelName, 1024);
    }

    public EmbeddingService(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            ObjectMapper objectMapper,
            String modelName,
            int dimensions
    ) {
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.modelName = modelName;
        this.dimensions = dimensions;
    }

    public String modelName() {
        return modelName;
    }

    public int dimensions() {
        return dimensions;
    }

    public boolean available() {
        return embeddingModel != null;
    }

    public float[] embed(String text) {
        if (embeddingModel == null) {
            throw new AiServiceException("向量模型未配置");
        }
        if (!StringUtils.hasText(text)) {
            throw new AiServiceException("无法为空文本生成向量");
        }
        try {
            float[] vector = embeddingModel.embed(text);
            if (vector == null || vector.length == 0) {
                throw new AiServiceException("向量模型未返回有效结果");
            }
            return vector;
        } catch (AiServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiServiceException("向量生成失败: " + summarize(ex), ex);
        }
    }

    private static String summarize(Throwable ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    public String toJson(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            throw new AiServiceException("向量序列化失败", ex);
        }
    }

    public float[] fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (JsonProcessingException ex) {
            throw new AiServiceException("向量反序列化失败", ex);
        }
    }

    public String describeProvider() {
        return modelName + "(" + dimensions + "d)";
    }
}
