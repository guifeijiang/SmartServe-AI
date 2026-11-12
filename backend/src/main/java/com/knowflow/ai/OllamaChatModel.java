package com.knowflow.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local Ollama chat adapter, intended for deepseek-r1:1.5b.
 * The RestClient is supplied by configuration so tests can intercept HTTP without network access.
 */
public class OllamaChatModel implements ChatModel {

    private static final int MAX_ERROR_BODY = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final double temperature;
    private final int numCtx;
    private final int numPredict;

    public OllamaChatModel(
            RestClient restClient,
            ObjectMapper objectMapper,
            String model,
            double temperature,
            int numCtx,
            int numPredict
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.temperature = temperature;
        this.numCtx = numCtx;
        this.numPredict = numPredict;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("temperature", temperature);
            options.put("num_ctx", numCtx);
            options.put("num_predict", numPredict);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", toMessages(prompt));
            body.put("stream", false);
            body.put("options", options);

            String raw = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(raw)) {
                throw new IllegalStateException("Ollama 未返回响应内容");
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException("Ollama 返回错误: " + truncate(error.asText()));
            }
            String content = root.path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                String thinking = root.path("message").path("thinking").asText("");
                String hint = StringUtils.hasText(thinking)
                        ? "（Ollama 只返回了思考内容，请增大 num-predict）"
                        : "";
                throw new IllegalStateException("Ollama 未返回有效回答" + hint);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content.strip()))));
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Ollama 对话模型调用失败: HTTP " + ex.getStatusCode().value() + " "
                            + truncate(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Ollama 对话响应解析失败", ex);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    private static List<Map<String, String>> toMessages(Prompt prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message message : prompt.getInstructions()) {
            String role = roleOf(message.getMessageType());
            if (role == null || !StringUtils.hasText(message.getText())) {
                continue;
            }
            messages.add(Map.of("role", role, "content", message.getText()));
        }
        if (messages.isEmpty()) {
            messages.add(Map.of("role", "user", "content", prompt.getContents()));
        }
        return messages;
    }

    private static String roleOf(MessageType type) {
        return switch (type) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> null;
        };
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_ERROR_BODY ? text.substring(0, MAX_ERROR_BODY) : text;
    }
}
