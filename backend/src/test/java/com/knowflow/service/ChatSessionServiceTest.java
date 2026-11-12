package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.ForbiddenException;
import com.knowflow.dto.ChatMessageResponse;
import com.knowflow.dto.ChatSessionResponse;
import com.knowflow.dto.CreateChatSessionRequest;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.dto.KnowledgeCitation;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long KB_ID = 10L;
    private static final Long SESSION_ID = 8L;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    private ChatSessionService chatSessionService;

    @BeforeEach
    void setUp() {
        chatSessionService = new ChatSessionService(
                knowledgeBaseService, chatSessionMapper, chatMessageMapper, new ObjectMapper()
        );
    }

    @Test
    void createBindsCurrentUserAndKnowledgeBase() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(chatSessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return 1;
        });

        ChatSessionResponse response = chatSessionService.create(OWNER_ID, KB_ID, new CreateChatSessionRequest());

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionMapper).insert(captor.capture());
        assertEquals(OWNER_ID, captor.getValue().getUserId());
        assertEquals(KB_ID, captor.getValue().getKnowledgeBaseId());
        assertEquals("新对话", captor.getValue().getTitle());
        assertEquals(SESSION_ID, response.getId());
    }

    @Test
    void listMessagesRejectedForOtherUser() {
        ChatSession session = ownedSession();
        session.setUserId(OWNER_ID);
        when(chatSessionMapper.findActiveById(SESSION_ID)).thenReturn(session);

        assertThrows(ForbiddenException.class, () -> chatSessionService.listMessages(OTHER_ID, SESSION_ID));
        verify(chatMessageMapper, never()).findBySessionId(any());
    }

    @Test
    void requireOwnedRejectsSessionFromAnotherKnowledgeBase() {
        when(chatSessionMapper.findActiveById(SESSION_ID)).thenReturn(ownedSession());

        assertThrows(ForbiddenException.class, () -> chatSessionService.requireOwned(OWNER_ID, 99L, SESSION_ID));
        verify(knowledgeBaseService, never()).getMine(OWNER_ID, 99L);
    }

    @Test
    void findRecentMessagesReturnsChronologicalOrder() {
        ChatMessage newer = message(2L, "后一句");
        ChatMessage older = message(1L, "先一句");
        when(chatMessageMapper.findRecentBySessionId(SESSION_ID, 10)).thenReturn(List.of(newer, older));

        List<ChatMessage> history = chatSessionService.findRecentMessages(SESSION_ID);

        assertEquals("先一句", history.get(0).getContent());
        assertEquals("后一句", history.get(1).getContent());
    }

    @Test
    void saveTurnPersistsUserAssistantAndCitedChunkIds() throws Exception {
        when(chatSessionMapper.findActiveById(SESSION_ID)).thenReturn(ownedSession());
        chatSessionService.requireOwned(OWNER_ID, KB_ID, SESSION_ID);

        chatSessionService.saveTurn(
                ownedSession(),
                "如何退款？",
                "请提供订单号",
                List.of(new KnowledgeCitation(11L, 1L, "refund.txt", 0, "退款需订单号", 0.9))
        );

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper, times(2)).insert(captor.capture());
        List<ChatMessage> saved = captor.getAllValues();
        assertEquals(ChatMessage.ROLE_USER, saved.get(0).getRole());
        assertEquals("如何退款？", saved.get(0).getContent());
        assertEquals(ChatMessage.ROLE_ASSISTANT, saved.get(1).getRole());
        assertEquals("请提供订单号", saved.get(1).getContent());
        assertEquals("[11]", saved.get(1).getCitedChunkIds());
        verify(chatSessionMapper).updateTitle(SESSION_ID, "如何退款？");
        verify(chatSessionMapper).touchUpdatedAt(SESSION_ID);
    }

    @Test
    void createRejectedWhenKnowledgeBaseNotOwned() {
        doThrow(new ForbiddenException("无权访问该知识库"))
                .when(knowledgeBaseService).getMine(OTHER_ID, KB_ID);

        assertThrows(ForbiddenException.class, () -> chatSessionService.create(OTHER_ID, KB_ID, new CreateChatSessionRequest()));
        verify(chatSessionMapper, never()).insert(any());
    }

    @Test
    void listMessagesReturnsPersistedHistory() {
        when(chatSessionMapper.findActiveById(SESSION_ID)).thenReturn(ownedSession());
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        ChatMessage user = message(1L, "如何退款？");
        user.setRole(ChatMessage.ROLE_USER);
        ChatMessage assistant = message(2L, "请提供订单号");
        assistant.setRole(ChatMessage.ROLE_ASSISTANT);
        assistant.setCitedChunkIds("[11]");
        when(chatMessageMapper.findBySessionId(SESSION_ID)).thenReturn(List.of(user, assistant));

        List<ChatMessageResponse> messages = chatSessionService.listMessages(OWNER_ID, SESSION_ID);

        assertEquals(2, messages.size());
        assertEquals(List.of(11L), messages.get(1).getCitedChunkIds());
    }

    private static KnowledgeBaseResponse kbResponse() {
        return new KnowledgeBaseResponse(KB_ID, "售后手册", "内部", "ACTIVE", null, null);
    }

    private static ChatSession ownedSession() {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(OWNER_ID);
        session.setKnowledgeBaseId(KB_ID);
        session.setTitle("新对话");
        return session;
    }

    private static ChatMessage message(Long id, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSessionId(SESSION_ID);
        message.setContent(content);
        return message;
    }
}
