package com.knowflow.service;

import com.knowflow.ai.ChatOutcome;
import com.knowflow.common.AiServiceException;
import com.knowflow.common.ForbiddenException;
import com.knowflow.dto.KnowledgeAskResponse;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.dto.KnowledgeCitation;
import com.knowflow.dto.RetrievalDiagnostics;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.rag.RagRetrievalService;
import com.knowflow.rag.RerankOutcome;
import com.knowflow.rag.RetrievalOutcome;
import com.knowflow.rag.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeQaServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long KB_ID = 10L;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private RagRetrievalService ragRetrievalService;

    private AiChatService aiChatService;

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ChatModel chatModel;

    private KnowledgeQaService knowledgeQaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        org.springframework.beans.factory.ObjectProvider<ChatModel> provider = mock(
                org.springframework.beans.factory.ObjectProvider.class
        );
        when(provider.getIfAvailable()).thenReturn(chatModel);
        aiChatService = new AiChatService(provider);
        org.mockito.Mockito.lenient().when(embeddingService.modelName()).thenReturn("text-embedding-v3");
        knowledgeQaService = new KnowledgeQaService(
                knowledgeBaseService,
                ragRetrievalService,
                aiChatService,
                chatSessionService,
                embeddingService
        );
    }

    @Test
    void askUsesRetrievedChunksAndBuildsPrompt() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(ragRetrievalService.retrieve(KB_ID, "如何退款？")).thenReturn(outcome(chunk(11L, "退款需提供订单号")));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse("请提供订单号办理退款"));

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "如何退款？");

        assertEquals("请提供订单号办理退款", response.getAnswer());
        assertEquals(1, response.getCitations().size());
        assertEquals("refund.txt", response.getCitations().get(0).getDocumentName());
        assertEquals("ANSWERED", response.getDiagnostics().getAnswerStatus());
        assertEquals("VECTOR", response.getDiagnostics().getRetrievalMode());
        assertEquals("LLM", response.getDiagnostics().getRerankStrategy());

        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> promptCaptor =
                ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String prompt = promptCaptor.getValue().getContents();
        assertTrue(prompt.contains("退款需提供订单号"));
        assertTrue(prompt.contains("不要编造"));
        verify(chatSessionService, never()).saveTurn(any(), any(), any(), any());
    }

    @Test
    void askReturnsFixedMessageWhenNoChunks() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(ragRetrievalService.retrieve(KB_ID, "如何退款？")).thenReturn(emptyOutcome());

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "如何退款？");

        assertEquals(KnowledgeQaService.NO_CONTEXT_ANSWER, response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
        assertEquals("NO_CONTEXT", response.getDiagnostics().getAnswerStatus());
        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void askRejectedWhenKnowledgeBaseNotOwned() {
        doThrow(new ForbiddenException("无权访问该知识库"))
                .when(knowledgeBaseService).getMine(OTHER_ID, KB_ID);

        assertThrows(ForbiddenException.class, () -> knowledgeQaService.ask(OTHER_ID, KB_ID, "如何退款？"));
        verify(ragRetrievalService, never()).retrieve(anyLong(), any());
        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    void askWithSessionUsesRecentHistoryAndPersistsMessages() {
        ChatSession session = session(8L);
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(chatSessionService.requireOwned(OWNER_ID, KB_ID, 8L)).thenReturn(session);
        when(chatSessionService.findRecentMessages(8L)).thenReturn(List.of(
                history(ChatMessage.ROLE_USER, "打印机怎么处理？"),
                history(ChatMessage.ROLE_ASSISTANT, "先检查卡纸")
        ));
        when(ragRetrievalService.retrieve(KB_ID, "然后呢？")).thenReturn(outcome(chunk(12L, "取出卡纸后重启")));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse("取出卡纸后重启即可"));

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "然后呢？", 8L);

        assertEquals(8L, response.getSessionId());
        assertEquals("取出卡纸后重启即可", response.getAnswer());
        ArgumentCaptor<RetrievalDiagnostics> diagnosticsCaptor =
                ArgumentCaptor.forClass(RetrievalDiagnostics.class);
        verify(chatSessionService).saveTurn(
                eq(session), eq("然后呢？"), eq("取出卡纸后重启即可"), any(), diagnosticsCaptor.capture()
        );
        assertNotNull(diagnosticsCaptor.getValue());
        assertEquals("LLM", diagnosticsCaptor.getValue().getRerankStrategy());
    }

    @Test
    void askClearsCitationsWhenModelRefuses() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(ragRetrievalService.retrieve(KB_ID, "保修期多久？")).thenReturn(outcome(chunk(11L, "退款需提供订单号")));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse(KnowledgeQaService.NO_CONTEXT_ANSWER));

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "保修期多久？");

        assertEquals(KnowledgeQaService.NO_CONTEXT_ANSWER, response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
        assertEquals("NO_CONTEXT", response.getDiagnostics().getAnswerStatus());
    }

    @Test
    void askUsesFallbackWhenModelReturnsEmptyContent() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(ragRetrievalService.retrieve(KB_ID, "如何退款？")).thenReturn(outcome(chunk(11L, "退款需提供订单号")));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse("   "));

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "如何退款？");

        assertEquals(KnowledgeQaService.EMPTY_ANSWER_FALLBACK, response.getAnswer());
        assertEquals("EMPTY_FALLBACK", response.getDiagnostics().getAnswerStatus());
        assertTrue(response.getCitations().isEmpty());
    }

    @Test
    void askHandsOffWhenModelFails() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(ragRetrievalService.retrieve(KB_ID, "如何退款？")).thenReturn(outcome(chunk(11L, "退款需提供订单号")));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenThrow(new RuntimeException("upstream timeout"));

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "如何退款？");

        assertEquals(KnowledgeQaService.HANDOFF_ANSWER, response.getAnswer());
        assertEquals("HANDOFF", response.getDiagnostics().getAnswerStatus());
    }

    @Test
    void toCitationsKeepsVectorAndRerankScores() {
        RetrievedChunk chunk = chunk(11L, "退款需提供订单号");
        chunk.setRerankScore(0.9d);

        List<KnowledgeCitation> citations = KnowledgeQaService.toCitations(List.of(chunk));

        assertEquals(0.9d, citations.get(0).getScore());
        assertEquals(0.88d, citations.get(0).getVectorScore());
        assertEquals(0.9d, citations.get(0).getRerankScore());
    }

    @Test
    void probeAiChatServiceDistinguishesEmptyFromFailure() {
        ChatOutcome empty = aiChatService.tryChat("  ");
        assertEquals(ChatOutcome.Status.FAILED, empty.getStatus());
        assertThrows(AiServiceException.class, () -> aiChatService.chat("  "));
    }

    @Test
    void refusalDetectionIgnoresTrailingRefusalSentence() {
        String answeredWithTrailingRefusal =
                "专属编号是 SSAI-REFUND-SAMPLE，退款需要四份材料。知识库中没有找到相关资料，无法回答该问题。";
        org.junit.jupiter.api.Assertions.assertFalse(KnowledgeQaService.isRefusal(answeredWithTrailingRefusal));
        assertTrue(KnowledgeQaService.isRefusal(KnowledgeQaService.NO_CONTEXT_ANSWER));
        org.junit.jupiter.api.Assertions.assertFalse(KnowledgeQaService.isRefusal("请提供订单号办理退款"));
    }

    @Test
    void followUpQuestionIsRewrittenWithPreviousQuestionForRetrieval() {
        List<ChatMessage> history = List.of(
                history(ChatMessage.ROLE_USER, "这项售后政策的专属测试编号是什么？"),
                history(ChatMessage.ROLE_ASSISTANT, "编号是 SSAI-REFUND-SAMPLE")
        );

        String rewritten = KnowledgeQaService.buildRetrievalQuery("刚才说的编号是多少？", history);
        assertTrue(rewritten.contains("专属测试编号"));
        assertTrue(rewritten.endsWith("刚才说的编号是多少？"));
        assertEquals("售后政策有效期多久？",
                KnowledgeQaService.buildRetrievalQuery("售后政策有效期多久？", history));
        assertEquals("刚才说的编号是多少？", KnowledgeQaService.buildRetrievalQuery("刚才说的编号是多少？", List.of()));
    }

    @Test
    void citationsAreTrimmedToWhatActuallyFitsPromptBudget() {
        org.springframework.test.util.ReflectionTestUtils.setField(knowledgeQaService, "contextMaxChars", 20);
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(ragRetrievalService.retrieve(KB_ID, "如何退款？")).thenReturn(outcome(
                chunk(11L, "退款需提供订单号，这是第一段足够长的正文"),
                chunk(12L, "第二段内容不应该进入提示词")
        ));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
                .thenReturn(chatResponse("办理退款需要提供订单号与购买凭证，请按页面提示提交申请。"));

        KnowledgeAskResponse response = knowledgeQaService.ask(OWNER_ID, KB_ID, "如何退款？");

        // 20 字预算刚好被第一段占满，引用列表也必须只有第一段，避免「引用了 Prompt 里没有的资料」。
        assertEquals(1, response.getCitations().size());
        assertEquals(11L, response.getCitations().get(0).getChunkId());

        ArgumentCaptor<org.springframework.ai.chat.prompt.Prompt> promptCaptor =
                ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String prompt = promptCaptor.getValue().getContents();
        assertTrue(prompt.contains("退款需提供订单号"));
        assertTrue(!prompt.contains("第二段内容不应该进入提示词"));
    }

    private static KnowledgeBaseResponse kbResponse() {
        return new KnowledgeBaseResponse(KB_ID, "售后手册", "内部", "ACTIVE", null, null);
    }

    private static ChatSession session(Long id) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(OWNER_ID);
        session.setKnowledgeBaseId(KB_ID);
        session.setTitle("新对话");
        return session;
    }

    private static ChatMessage history(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static RetrievalOutcome outcome(RetrievedChunk... chunks) {
        return new RetrievalOutcome(
                RetrievalOutcome.MODE_VECTOR, false, null, chunks.length,
                "mysql-cosine", RerankOutcome.STRATEGY_LLM, List.of(chunks)
        );
    }

    private static RetrievalOutcome emptyOutcome() {
        return new RetrievalOutcome(
                RetrievalOutcome.MODE_NONE, true, "LOW_SIMILARITY", 0,
                "mysql-cosine", RerankOutcome.STRATEGY_NONE, List.of()
        );
    }

    private static RetrievedChunk chunk(Long chunkId, String content) {
        return new RetrievedChunk(chunkId, 11L, "refund.txt", 0, content, 0.88d);
    }

    private static org.springframework.ai.chat.model.ChatResponse chatResponse(String text) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage(text)
                )
        ));
    }
}
