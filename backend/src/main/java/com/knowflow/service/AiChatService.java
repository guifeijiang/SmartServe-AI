package com.knowflow.service;

import com.knowflow.ai.ChatOutcome;
import com.knowflow.common.AiServiceException;
import com.knowflow.dto.AiChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatService {

    private final ChatModel chatModel;

    public AiChatService(ObjectProvider<ChatModel> chatModelProvider) {
        this.chatModel = chatModelProvider.getIfAvailable();
    }

    public AiChatResponse chat(String message) {
        ChatOutcome outcome = tryChat(message);
        if (outcome.isAnswered()) {
            return new AiChatResponse(outcome.getAnswer());
        }
        if (outcome.getStatus() == ChatOutcome.Status.EMPTY) {
            throw new AiServiceException("模型未返回有效内容");
        }
        throw new AiServiceException("AI 服务调用失败: " + summarize(outcome.getError()));
    }

    /**
     * 不抛异常版本：把「空内容」和「调用失败/超时」区分开，交给 RAG 做不同的降级处理。
     */
    public ChatOutcome tryChat(String message) {
        if (chatModel == null) {
            return ChatOutcome.failed("AI 服务未配置");
        }
        String promptText = message == null ? "" : message.trim();
        if (!StringUtils.hasText(promptText)) {
            return ChatOutcome.failed("消息不能为空");
        }
        try {
            ChatResponse response = chatModel.call(new Prompt(promptText));
            String answer = extractAnswer(response);
            if (!StringUtils.hasText(answer)) {
                return ChatOutcome.empty();
            }
            return ChatOutcome.answered(answer);
        } catch (RuntimeException ex) {
            return ChatOutcome.failed(summarize(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private static String summarize(String message) {
        if (!StringUtils.hasText(message)) {
            return "未知错误";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static String extractAnswer(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        Generation generation = response.getResult();
        AssistantMessage output = generation.getOutput();
        if (output == null) {
            return null;
        }
        return output.getText();
    }
}
