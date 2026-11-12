package com.knowflow.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local Ollama embedding adapter. Default model is BGE-M3 (1024 dimensions).
 * Vectors stay in the existing document_chunk.embedding_json JSON column.
 */
public class OllamaEmbeddingModel implements EmbeddingModel {

    private static final int MAX_ERROR_BODY = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int dimensions;

    public OllamaEmbeddingModel(RestClient restClient, ObjectMapper objectMapper, String model, int dimensions) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return embedForResponse(request.getInstructions());
    }

    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("文本不能为空");
        }
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("文档不能为空");
        }
        return embed(document.getText());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return embedBatch(texts);
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        List<float[]> vectors = embedBatch(texts);
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            throw new IllegalArgumentException("文本列表不能为空");
        }
        try {
            String raw = restClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", model, "input", texts))
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(raw)) {
                throw new IllegalStateException("Ollama 未返回向量结果");
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException("Ollama 返回错误: " + truncate(error.asText()));
            }
            JsonNode vectors = root.path("embeddings");
            if (!vectors.isArray() || vectors.size() != texts.size()) {
                throw new IllegalStateException("Ollama 返回的向量数量与输入不一致");
            }
            List<float[]> result = new ArrayList<>(vectors.size());
            for (JsonNode vectorNode : vectors) {
                if (!vectorNode.isArray() || vectorNode.isEmpty()) {
                    throw new IllegalStateException("Ollama 返回了空向量");
                }
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) vectorNode.get(i).asDouble();
                }
                result.add(vector);
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Ollama 向量模型调用失败: HTTP " + ex.getStatusCode().value() + " " + truncate(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Ollama 向量响应解析失败", ex);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_ERROR_BODY ? text.substring(0, MAX_ERROR_BODY) : text;
    }
}
