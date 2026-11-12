package com.knowflow.service;

import com.knowflow.ai.ChatOutcome;
import com.knowflow.dto.KnowledgeAskResponse;
import com.knowflow.dto.KnowledgeCitation;
import com.knowflow.dto.RetrievalDiagnostics;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.rag.RagRetrievalService;
import com.knowflow.rag.RetrievalOutcome;
import com.knowflow.rag.RetrievedChunk;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RAG 问答：向量召回 → 精排 → Prompt → 模型回答，并在失败时执行三级降级。
 *
 * 降级顺序：
 * 1) 检索层：相似度低于阈值 / 向量不可用 → MySQL 全文检索（由 {@link RagRetrievalService} 完成）；
 * 2) 检索无结果 → 固定话术 {@link #NO_CONTEXT_ANSWER}；
 * 3) 模型返回空内容 → 固定兜底 {@link #EMPTY_ANSWER_FALLBACK}；
 * 4) 模型调用异常或超时 → {@link #HANDOFF_ANSWER} 提示转人工。
 */
@Service
public class KnowledgeQaService {

    public static final String NO_CONTEXT_ANSWER = "知识库中没有找到相关资料，无法回答该问题。";
    public static final String EMPTY_ANSWER_FALLBACK = "抱歉，本轮没有生成有效回答，请换一种问法重试，或转人工客服。";
    public static final String HANDOFF_ANSWER = "AI 服务暂时不可用（调用失败或超时），已为你转接人工客服，请稍候。";

    private final KnowledgeBaseService knowledgeBaseService;
    private final RagRetrievalService ragRetrievalService;
    private final AiChatService aiChatService;
    private final ChatSessionService chatSessionService;
    private final EmbeddingService embeddingService;

    /**
     * 注入 Prompt 的原文总字数上限，控制上下文长度与 token 成本（可直接用 @Value 注入，避免改构造签名影响测试）。
     */
    @org.springframework.beans.factory.annotation.Value("${knowflow.rag.context-max-chars:1500}")
    private int contextMaxChars = 1500;

    public KnowledgeQaService(
            KnowledgeBaseService knowledgeBaseService,
            RagRetrievalService ragRetrievalService,
            AiChatService aiChatService,
            ChatSessionService chatSessionService,
            EmbeddingService embeddingService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.ragRetrievalService = ragRetrievalService;
        this.aiChatService = aiChatService;
        this.chatSessionService = chatSessionService;
        this.embeddingService = embeddingService;
    }

    public KnowledgeAskResponse ask(Long userId, Long knowledgeBaseId, String message) {
        return ask(userId, knowledgeBaseId, message, null);
    }

    public KnowledgeAskResponse ask(Long userId, Long knowledgeBaseId, String message, Long sessionId) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        ChatSession session = null;
        List<ChatMessage> history = List.of();
        if (sessionId != null) {
            session = chatSessionService.requireOwned(userId, knowledgeBaseId, sessionId);
            history = chatSessionService.findRecentMessages(session.getId());
        }
        String question = message == null ? "" : message.trim();

        // 多轮追问的检索增强：像「刚才说的编号是多少」这类短追问，单独去做向量检索几乎必然跑偏，
        // 这里把上一轮用户问题拼进检索语句（只影响召回，不改变最终回答的问题）。
        String retrievalQuery = buildRetrievalQuery(question, history);
        RetrievalOutcome retrieval = ragRetrievalService.retrieve(knowledgeBaseId, retrievalQuery);
        // 只保留真正能放进 Prompt 的片段，避免返回给前端的引用里出现 Prompt 根本没带上的内容。
        List<KnowledgeCitation> citations = withinContextBudget(toCitations(retrieval.getChunks()));

        String answer;
        String answerStatus;
        if (citations.isEmpty()) {
            answer = NO_CONTEXT_ANSWER;
            answerStatus = "NO_CONTEXT";
        } else {
            ChatOutcome outcome = aiChatService.tryChat(buildPrompt(question, citations, history));
            switch (outcome.getStatus()) {
                case ANSWERED -> {
                    answer = outcome.getAnswer();
                    if (isRefusal(answer)) {
                        // 模型整轮拒答时，不把召回到的片段当作引用来源。
                        citations = List.of();
                        answerStatus = "NO_CONTEXT";
                    } else {
                        answerStatus = "ANSWERED";
                    }
                }
                case EMPTY -> {
                    answer = EMPTY_ANSWER_FALLBACK;
                    answerStatus = "EMPTY_FALLBACK";
                    citations = List.of();
                }
                default -> {
                    answer = HANDOFF_ANSWER;
                    answerStatus = "HANDOFF";
                    citations = List.of();
                }
            }
        }

        RetrievalDiagnostics diagnostics = buildDiagnostics(retrieval, citations.size(), answerStatus);
        if (session != null) {
            chatSessionService.saveTurn(session, question, answer, citations, diagnostics);
        }
        KnowledgeAskResponse response = new KnowledgeAskResponse(
                session == null ? null : session.getId(),
                answer,
                citations
        );
        response.setDiagnostics(diagnostics);
        return response;
    }

    /**
     * 判断模型是否整轮拒答：把固定拒答句剔除后若几乎没剩内容，才算拒答；
     * 只看「答案里出现过这句话」会把「先正常回答、末尾又补一句没找到」的答案误判成拒答。
     */
    static boolean isRefusal(String answer) {
        if (!StringUtils.hasText(answer)) {
            return false;
        }
        String stripped = answer
                .replace("知识库中没有找到相关资料", "")
                .replace("无法回答该问题", "")
                .replaceAll("[\\s，。,.!！?？：:；;]", "");
        return stripped.length() < 10;
    }

    private static final List<String> ANAPHORA_MARKERS = List.of(
            "刚才", "刚刚", "之前", "上面", "前面", "它", "这个", "那个", "该", "上述", "这条", "那条"
    );

    /**
     * 多轮追问的检索改写：短问题里出现指代/回溯词时，把最近一条用户提问拼到检索语句前面。
     */
    static String buildRetrievalQuery(String question, List<ChatMessage> history) {
        if (history == null || history.isEmpty() || !StringUtils.hasText(question) || question.length() > 30) {
            return question;
        }
        boolean anaphoric = ANAPHORA_MARKERS.stream().anyMatch(question::contains);
        if (!anaphoric) {
            return question;
        }
        String lastUserMessage = null;
        for (ChatMessage message : history) {
            if (ChatMessage.ROLE_USER.equals(message.getRole()) && StringUtils.hasText(message.getContent())) {
                lastUserMessage = message.getContent();
            }
        }
        if (lastUserMessage == null || lastUserMessage.equals(question)) {
            return question;
        }
        return lastUserMessage + " " + question;
    }

    /**
     * 按 contextMaxChars 预算裁剪片段列表：返回的就是真正会进入 Prompt 的那几段。
     * 第一个片段即使超预算也会保留（Prompt 里会截断），保证不会出现「有召回但零引用」。
     */
    List<KnowledgeCitation> withinContextBudget(List<KnowledgeCitation> candidates) {
        if (candidates == null || candidates.isEmpty() || contextMaxChars <= 0) {
            return candidates == null ? List.of() : candidates;
        }
        int budget = contextMaxChars;
        List<KnowledgeCitation> kept = new ArrayList<>(candidates.size());
        for (KnowledgeCitation citation : candidates) {
            if (budget <= 0 && !kept.isEmpty()) {
                break;
            }
            kept.add(citation);
            int length = safe(citation.getContent()).length();
            budget -= Math.min(length, budget);
        }
        return kept;
    }

    private RetrievalDiagnostics buildDiagnostics(RetrievalOutcome retrieval, int rerankedCount, String answerStatus) {
        RetrievalDiagnostics diagnostics = new RetrievalDiagnostics();
        diagnostics.setRetrievalMode(retrieval.getRetrievalMode());
        diagnostics.setDegraded(retrieval.isDegraded());
        diagnostics.setDegradeReason(retrieval.getDegradeReason());
        diagnostics.setRecalledCount(retrieval.getRecalledCount());
        diagnostics.setRerankedCount(rerankedCount);
        diagnostics.setRerankStrategy(retrieval.getRerankStrategy());
        diagnostics.setIndexType(retrieval.getIndexType());
        diagnostics.setAnswerStatus(answerStatus);
        diagnostics.setEmbeddingModel(embeddingService.modelName());
        return diagnostics;
    }

    static List<KnowledgeCitation> toCitations(List<RetrievedChunk> chunks) {
        List<KnowledgeCitation> citations = new ArrayList<>(chunks.size());
        for (RetrievedChunk chunk : chunks) {
            KnowledgeCitation citation = new KnowledgeCitation(
                    chunk.getChunkId(),
                    chunk.getDocumentId(),
                    chunk.getDocumentName(),
                    chunk.getChunkIndex(),
                    chunk.getContent(),
                    chunk.getRerankScore() != null ? chunk.getRerankScore() : chunk.getVectorScore()
            );
            citation.setVectorScore(round(chunk.getVectorScore()));
            citation.setRerankScore(round(chunk.getRerankScore()));
            citation.setScore(round(citation.getScore()));
            citations.add(citation);
        }
        return citations;
    }

    String buildPrompt(String question, List<KnowledgeCitation> citations) {
        return buildPrompt(question, citations, List.of());
    }

    String buildPrompt(String question, List<KnowledgeCitation> citations, List<ChatMessage> history) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是企业售后助手。请严格遵守以下规则：\n")
                .append("1. 只回答【当前问题】，不要重复回答历史问题。\n")
                .append("2. 对于知识性问题，只允许使用【知识库资料】中的原文信息回答。回答前先找出资料中与问题直接相关的原句，并在回答中引用该原句。\n")
                .append("3. 只有整段【知识库资料】都无法回答【当前问题】时，才输出这一句：知识库中没有找到相关资料，无法回答该问题。如果已经给出答案，不要再补这句话。\n")
                .append("4. 禁止根据常识、对话历史或上下文进行推断；不要编造任何数字、期限、地址、编号或政策。\n")
                .append("5. 【对话历史】只用于理解指代和追问；若【当前问题】是「刚才说的…」「它是什么」这类追问，请先用【对话历史】中已有的回答直接作答，不要回答没有找到。\n")
                .append("6. 回答时不要输出【知识库资料】【对话历史】这类内部标记，也不要说明资料来自第几段，直接用自然语言回答。");
        if (history != null && !history.isEmpty()) {
            prompt.append("\n\n【对话历史】\n");
            for (ChatMessage message : history) {
                prompt.append(message.getRole())
                        .append("：")
                        .append(safe(message.getContent()))
                        .append('\n');
            }
        }
        prompt.append("\n【知识库资料】\n");
        int budget = contextMaxChars > 0 ? contextMaxChars : Integer.MAX_VALUE;
        for (int i = 0; i < citations.size(); i++) {
            KnowledgeCitation citation = citations.get(i);
            String content = safe(citation.getContent());
            if (budget <= 0) {
                break;
            }
            if (content.length() > budget) {
                content = content.substring(0, budget) + "…";
            }
            budget -= Math.min(content.length(), budget);
            prompt.append(String.format(Locale.ROOT, "[%d] 文档：%s（ID=%s，分块=%s）\n%s\n\n",
                    i + 1,
                    safe(citation.getDocumentName()),
                    citation.getDocumentId(),
                    citation.getChunkIndex(),
                    content));
        }
        prompt.append("【当前问题】\n").append(question);
        return prompt.toString();
    }

    private static String safe(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private static Double round(Double score) {
        if (score == null) {
            return null;
        }
        return Math.round(score * 10000d) / 10000d;
    }
}
