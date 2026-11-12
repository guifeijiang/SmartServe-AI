package com.knowflow.service;

import com.knowflow.common.AiServiceException;
import com.knowflow.dto.AiChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private ChatModel chatModel;

    private AiChatService aiChatService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<ChatModel> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        aiChatService = new AiChatService(provider);
    }

    @Test
    void chatReturnsModelAnswerWithoutCallingNetwork() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("售后工单可在客户工作台提交"));

        AiChatResponse response = aiChatService.chat("如何提交售后工单？");

        assertEquals("售后工单可在客户工作台提交", response.getAnswer());
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void chatRejectsBlankMessage() {
        assertThrows(AiServiceException.class, () -> aiChatService.chat("   "));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void chatWrapsModelFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("upstream timeout"));

        AiServiceException ex = assertThrows(AiServiceException.class, () -> aiChatService.chat("你好"));
        assertTrue(ex.getMessage().startsWith("AI 服务调用失败"));
        assertTrue(ex.getMessage().contains("upstream timeout"));
    }

    @Test
    void chatRejectsEmptyModelOutput() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("  "));

        assertThrows(AiServiceException.class, () -> aiChatService.chat("你好"));
    }

    private static ChatResponse chatResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
