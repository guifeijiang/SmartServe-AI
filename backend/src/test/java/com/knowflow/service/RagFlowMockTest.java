package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.ai.DeepSeekChatModel;
import com.knowflow.ai.OllamaEmbeddingModel;
import com.knowflow.config.AiProperties;
import com.knowflow.config.RagProperties;
import com.knowflow.dto.KnowledgeAskResponse;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.RetrievableChunk;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.rag.RagRetrievalService;
import com.knowflow.rag.RerankService;
import com.knowflow.rag.RetrievalOutcome;
import com.knowflow.rag.vector.MysqlCosineVectorIndex;
import com.knowflow.rag.vector.VectorIndexRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 端到端 RAG 链路（Mock HTTP + 真实适配器/检索/精排代码）：
 * bge-m3 向量化 → 余弦召回 Top-5 → LLM 精排打分 → Top-3 进 Prompt → DeepSeek 生成回答 → 保存会话。
 */
@ExtendWith(MockitoExtension.class)
class RagFlowMockTest {

    private static final Long USER_ID = 1L;
    private static final Long KB_ID = 10L;
    private static final Long SESSION_ID = 8L;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private VectorIndexRouter vectorIndexRouter;

    private MockRestServiceServer ollamaServer;
    private MockRestServiceServer deepSeekServer;
    private KnowledgeQaService knowledgeQaService;
    private RagRetrievalService ragRetrievalService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiProperties aiProperties = new AiProperties();

        RestClient.Builder ollamaBuilder = RestClient.builder().baseUrl("http://127.0.0.1:11434");
        ollamaServer = MockRestServiceServer.bindTo(ollamaBuilder).build();
        OllamaEmbeddingModel embeddingModel = new OllamaEmbeddingModel(
                ollamaBuilder.build(), objectMapper, "bge-m3", 1024
        );
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        EmbeddingService embeddingService = new EmbeddingService(
                embeddingModelProvider, objectMapper, aiProperties
        );

        RestClient.Builder chatBuilder = RestClient.builder()
                .baseUrl("https://api.deepseek.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        deepSeekServer = MockRestServiceServer.bindTo(chatBuilder).build();
        DeepSeekChatModel chatModel = new DeepSeekChatModel(
                chatBuilder.build(), objectMapper, "deepseek-chat", 0.2
        );
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        AiChatService aiChatService = new AiChatService(chatModelProvider);

        RagProperties ragProperties = new RagProperties();
        when(vectorIndexRouter.type()).thenReturn("mysql-cosine");
        when(vectorIndexRouter.active()).thenReturn(new MysqlCosineVectorIndex(documentChunkMapper, embeddingService));

        RerankService rerankService = new RerankService(chatModelProvider, ragProperties);
        ragRetrievalService = new RagRetrievalService(
                embeddingService, vectorIndexRouter, documentChunkMapper, rerankService, ragProperties
        );
        knowledgeQaService = new KnowledgeQaService(
                knowledgeBaseService,
                ragRetrievalService,
                aiChatService,
                chatSessionService,
                embeddingService
        );
    }

    @Test
    void ragAnswerUsesEmbeddingRecallRerankAndChatWithHistory() {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setKnowledgeBaseId(KB_ID);
        session.setTitle("售后对话");

        when(knowledgeBaseService.getMine(USER_ID, KB_ID))
                .thenReturn(new KnowledgeBaseResponse(KB_ID, "售后手册", "内部", "ACTIVE", null, null));
        when(chatSessionService.requireOwned(USER_ID, KB_ID, SESSION_ID)).thenReturn(session);
        when(chatSessionService.findRecentMessages(SESSION_ID)).thenReturn(List.of(
                history(ChatMessage.ROLE_USER, "第一轮：打印机卡纸怎么办？"),
                history(ChatMessage.ROLE_ASSISTANT, "先取出卡纸")
        ));
        when(documentChunkMapper.findReadyByKnowledgeBaseId(KB_ID)).thenReturn(List.of(
                chunk(101L, 11L, 0, "退款需提供订单号并说明下单渠道", "[1,0,0]"),
                chunk(102L, 11L, 1, "打印机卡纸时先取出纸张", "[0,1,0]"),
                chunk(103L, 11L, 2, "售后受理时间为工作日 9:00-18:00", "[0,0,1]")
        ));
        when(documentChunkMapper.findByIds(any())).thenReturn(List.of(
                chunk(101L, 11L, 0, "退款需提供订单号并说明下单渠道", "[1,0,0]"),
                chunk(102L, 11L, 1, "打印机卡纸时先取出纸张", "[0,1,0]"),
                chunk(103L, 11L, 2, "售后受理时间为工作日 9:00-18:00", "[0,0,1]")
        ));

        ollamaServer.expect(requestTo("http://127.0.0.1:11434/api/embed"))
                .andExpect(jsonPath("$.model").value("bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("退款流程是什么？"))
                .andRespond(withSuccess("{\"embeddings\":[[1.0,0.0,0.0]]}", MediaType.APPLICATION_JSON));

        // 第一次对话调用是精排：模型只输出候选编号和分数
        deepSeekServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content", containsString("检索结果重排器")))
                .andExpect(jsonPath("$.messages[0].content", containsString("退款需提供订单号")))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"1:9\\n2:2\\n3:3\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        // 第二次对话调用才是最终回答，Prompt 带 Top-3 片段与最近 10 条历史
        deepSeekServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.messages[0].content", containsString("退款需提供订单号")))
                .andExpect(jsonPath("$.messages[0].content", containsString("第一轮：打印机卡纸怎么办？")))
                .andExpect(jsonPath("$.messages[0].content", containsString("不要编造")))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"请提供订单号办理退款\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        KnowledgeAskResponse response = knowledgeQaService.ask(USER_ID, KB_ID, "退款流程是什么？", SESSION_ID);

        assertEquals("请提供订单号办理退款", response.getAnswer());
        assertEquals(3, response.getCitations().size());
        assertEquals("退款需提供订单号并说明下单渠道", response.getCitations().get(0).getContent());
        assertEquals("LLM", response.getDiagnostics().getRerankStrategy());
        assertEquals(3, response.getDiagnostics().getRecalledCount());
        assertTrue(response.getCitations().get(0).getScore() > 0.8d);
        verify(chatSessionService).saveTurn(eq(session), eq("退款流程是什么？"), eq("请提供订单号办理退款"),
                any(), any());
        ollamaServer.verify();
        deepSeekServer.verify();
    }

    @Test
    void retrievalFallsBackToFulltextWhenTopScoreBelowThreshold() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagProperties ragProperties = new RagProperties();
        ragProperties.setRerankStrategy("bm25");
        RerankService rerankService = new RerankService(chatModelProvider, ragProperties);
        when(embeddingService.embed("退货期限多久？")).thenReturn(new float[] {1f, 0f});
        when(embeddingService.modelName()).thenReturn("bge-m3");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(KB_ID)).thenReturn(List.of(
                chunk(101L, 11L, 0, "完全不相关的内容", "[0,1]")
        ));
        when(documentChunkMapper.findByIds(any())).thenReturn(List.of(
                chunk(101L, 11L, 0, "完全不相关的内容", "[0,1]")
        ));
        when(documentChunkMapper.searchFulltext(eq(KB_ID), eq("退货期限多久？"), eq("bge-m3"), eq(5)))
                .thenReturn(List.of(chunk(201L, 12L, 0, "退货期限为签收后 7 天", "[0,1]")));

        RagRetrievalService service = new RagRetrievalService(
                embeddingService, vectorIndexRouter, documentChunkMapper, rerankService, ragProperties
        );
        RetrievalOutcome outcome = service.retrieve(KB_ID, "退货期限多久？");

        assertEquals(RetrievalOutcome.MODE_FULLTEXT, outcome.getRetrievalMode());
        assertEquals("LOW_SIMILARITY", outcome.getDegradeReason());
        assertTrue(outcome.isDegraded());
        assertTrue(outcome.getChunks().get(0).getContent().contains("7 天"));
        verify(documentChunkMapper).searchFulltext(eq(KB_ID), eq("退货期限多久？"), eq("bge-m3"), eq(5));
    }

    private static ChatMessage history(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static RetrievableChunk chunk(Long chunkId, Long documentId, int index, String content, String embeddingJson) {
        RetrievableChunk chunk = new RetrievableChunk();
        chunk.setId(chunkId);
        chunk.setDocumentId(documentId);
        chunk.setKnowledgeBaseId(KB_ID);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setEmbeddingJson(embeddingJson);
        chunk.setEmbeddingModel("bge-m3");
        chunk.setOriginalFilename("refund.txt");
        return chunk;
    }
}
