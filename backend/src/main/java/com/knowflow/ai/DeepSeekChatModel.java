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
 * DeepSeek uses an OpenAI-compatible chat completion API.
 * The RestClient is supplied by configuration so tests can intercept HTTP without network access.
 */
public class DeepSeekChatModel implements ChatModel {

    private static final int MAX_ERROR_BODY = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final double temperature;

    public DeepSeekChatModel(RestClient restClient, ObjectMapper objectMapper, String model, double temperature) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", toMessages(prompt));
            body.put("temperature", temperature);
            body.put("stream", false);

            String raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(raw)) {
                throw new IllegalStateException("DeepSeek 未返回响应内容");
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                throw new IllegalStateException("DeepSeek API 返回错误: " + truncate(error.toString()));
            }
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("DeepSeek 未返回有效回答");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "DeepSeek API 调用失败: HTTP " + ex.getStatusCode().value() + " " + truncate(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("DeepSeek 响应解析失败", ex);
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
