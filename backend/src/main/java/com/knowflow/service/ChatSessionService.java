package com.knowflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.ForbiddenException;
import com.knowflow.common.ResourceNotFoundException;
import com.knowflow.dto.ChatMessageResponse;
import com.knowflow.dto.ChatSessionResponse;
import com.knowflow.dto.CreateChatSessionRequest;
import com.knowflow.dto.KnowledgeCitation;
import com.knowflow.dto.RetrievalDiagnostics;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatSessionService {

    static final int HISTORY_LIMIT = 10;
    static final String DEFAULT_TITLE = "新对话";

    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    public ChatSessionService(
            KnowledgeBaseService knowledgeBaseService,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatSessionResponse create(Long userId, Long knowledgeBaseId, CreateChatSessionRequest request) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setKnowledgeBaseId(knowledgeBaseId);
        session.setTitle(resolveTitle(request == null ? null : request.getTitle(), null));
        chatSessionMapper.insert(session);
        return toSessionResponse(session);
    }

    public List<ChatSessionResponse> listMine(Long userId, Long knowledgeBaseId) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        return chatSessionMapper.findActiveByUserIdAndKnowledgeBaseId(userId, knowledgeBaseId).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    public List<ChatMessageResponse> listMessages(Long userId, Long sessionId) {
        requireOwned(userId, null, sessionId);
        return chatMessageMapper.findBySessionId(sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    public ChatSession requireOwned(Long userId, Long knowledgeBaseId, Long sessionId) {
        ChatSession session = chatSessionMapper.findActiveById(sessionId);
        if (session == null) {
            throw new ResourceNotFoundException("会话不存在");
        }
        if (!userId.equals(session.getUserId())) {
            throw new ForbiddenException("无权访问该会话");
        }
        if (knowledgeBaseId != null && !knowledgeBaseId.equals(session.getKnowledgeBaseId())) {
            throw new ForbiddenException("会话不属于该知识库");
        }
        knowledgeBaseService.getMine(userId, session.getKnowledgeBaseId());
        return session;
    }

    public List<ChatMessage> findRecentMessages(Long sessionId) {
        List<ChatMessage> recent = chatMessageMapper.findRecentBySessionId(sessionId, HISTORY_LIMIT);
        if (recent == null || recent.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological;
    }

    @Transactional
    public void saveTurn(ChatSession session, String userMessage, String assistantMessage, List<KnowledgeCitation> citations) {
        saveTurn(session, userMessage, assistantMessage, citations, null);
    }

    @Transactional
    public void saveTurn(
            ChatSession session,
            String userMessage,
            String assistantMessage,
            List<KnowledgeCitation> citations,
            RetrievalDiagnostics diagnostics
    ) {
        ChatMessage user = new ChatMessage();
        user.setSessionId(session.getId());
        user.setRole(ChatMessage.ROLE_USER);
        user.setContent(userMessage);
        chatMessageMapper.insert(user);

        ChatMessage assistant = new ChatMessage();
        assistant.setSessionId(session.getId());
        assistant.setRole(ChatMessage.ROLE_ASSISTANT);
        assistant.setContent(assistantMessage);
        assistant.setCitedChunkIds(toCitedJson(citations));
        assistant.setRetrievalMeta(toMetaJson(diagnostics));
        chatMessageMapper.insert(assistant);

        if (!StringUtils.hasText(session.getTitle()) || DEFAULT_TITLE.equals(session.getTitle())) {
            chatSessionMapper.updateTitle(session.getId(), resolveTitle(null, userMessage));
        }
        chatSessionMapper.touchUpdatedAt(session.getId());
    }

    private String toMetaJson(RetrievalDiagnostics diagnostics) {
        if (diagnostics == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(diagnostics);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("检索诊断序列化失败", ex);
        }
    }

    private RetrievalDiagnostics parseMeta(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RetrievalDiagnostics.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String toCitedJson(List<KnowledgeCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        List<Long> ids = citations.stream()
                .map(KnowledgeCitation::getChunkId)
                .filter(id -> id != null)
                .toList();
        if (ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("引用分块序列化失败", ex);
        }
    }

    private List<Long> parseCitedIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(session.getId());
        response.setUserId(session.getUserId());
        response.setKnowledgeBaseId(session.getKnowledgeBaseId());
        response.setTitle(session.getTitle());
        response.setCreatedAt(session.getCreatedAt());
        response.setUpdatedAt(session.getUpdatedAt());
        return response;
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.setId(message.getId());
        response.setSessionId(message.getSessionId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setCitedChunkIds(parseCitedIds(message.getCitedChunkIds()));
        response.setRetrieval(parseMeta(message.getRetrievalMeta()));
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    private static String resolveTitle(String requested, String fallbackMessage) {
        String title = StringUtils.hasText(requested) ? requested.trim() : fallbackMessage;
        if (!StringUtils.hasText(title)) {
            return DEFAULT_TITLE;
        }
        return title.length() > 255 ? title.substring(0, 255) : title;
    }
}
