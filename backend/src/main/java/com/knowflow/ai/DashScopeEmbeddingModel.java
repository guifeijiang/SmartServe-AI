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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史自研适配器（OpenAI 兼容的 /compatible-mode/v1/embeddings）。
 *
 * <p>运行时已切换为 Spring AI Alibaba 内置的
 * {@code com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel}（text-embedding-v3），
 * 本类不再被 {@code AiModelConfig} 引用，仅作为兼容模式对照保留。</p>
 */
@Deprecated
public class DashScopeEmbeddingModel implements EmbeddingModel {

    private static final int MAX_ERROR_BODY = 300;
    private static final String EMBEDDINGS_PATH = "/compatible-mode/v1/embeddings";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int dimensions;

    public DashScopeEmbeddingModel(
            RestClient restClient,
            ObjectMapper objectMapper,
            String model,
            int dimensions
    ) {
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        body.put("dimensions", dimensions);
        body.put("encoding_format", "float");
        try {
            String raw = restClient.post()
                    .uri(EMBEDDINGS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(raw)) {
                throw new IllegalStateException("DashScope 未返回向量结果");
            }
            JsonNode root = objectMapper.readTree(raw);
            if (!root.path("error").isMissingNode() && !root.path("error").isNull()) {
                throw new IllegalStateException("DashScope 返回错误: " + truncate(root.path("error").toString()));
            }
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                throw new IllegalStateException("DashScope 返回的向量数量与输入不一致");
            }
            List<float[]> result = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.isEmpty()) {
                    throw new IllegalStateException("DashScope 返回了空向量");
                }
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < vector.length; i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                result.add(vector);
            }
            return result;
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "DashScope 向量模型调用失败: HTTP " + ex.getStatusCode().value() + " "
                            + truncate(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("DashScope 向量响应解析失败", ex);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_ERROR_BODY ? text.substring(0, MAX_ERROR_BODY) : text;
    }
}
