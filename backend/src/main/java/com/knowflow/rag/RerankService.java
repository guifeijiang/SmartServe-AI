package com.knowflow.rag;

import com.knowflow.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真正的二次相关性排序（精排），与向量余弦召回是两个独立阶段：
 *
 * 1) LLM 精排：把「问题 + 候选片段」交给对话模型，让它对每条候选打 0-10 的相关性分（listwise rerank）。
 *    只接受「全部候选都被打分」的结果，否则视为失败并回退，避免把不完整结果当成精排。
 * 2) BM25 精排：词法相关性排序（中文按 2 字 shingle、英文按单词），用于模型不可用或输出不可解析时兜底。
 *
 * 无论走哪条路径，都会把实际生效的策略写进 {@link RerankOutcome#getStrategy()}，便于验收区分。
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private static final Pattern SCORE_LINE = Pattern.compile("(\\d+)\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)");
    private static final int SNIPPET_LIMIT = 300;
    private static final double BM25_K1 = 1.2d;
    private static final double BM25_B = 0.75d;

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "请", "我", "你", "他", "她", "它",
            "the", "a", "an", "is", "are", "to", "of", "and", "for", "in", "on"
    );

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final RagProperties ragProperties;

    public RerankService(ObjectProvider<ChatModel> chatModelProvider, RagProperties ragProperties) {
        this.chatModelProvider = chatModelProvider;
        this.ragProperties = ragProperties;
    }

    public RerankOutcome rerank(String question, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return new RerankOutcome(RerankOutcome.STRATEGY_NONE, List.of());
        }
        String configured = ragProperties.getRerankStrategy() == null
                ? "auto"
                : ragProperties.getRerankStrategy().trim().toLowerCase(Locale.ROOT);

        if ("bm25".equals(configured)) {
            return bm25(question, candidates, topN);
        }
        RerankOutcome llm = "llm".equals(configured) || "auto".equals(configured)
                ? tryLlmRerank(question, candidates, topN)
                : null;
        if (llm != null) {
            return llm;
        }
        return bm25(question, candidates, topN);
    }

    private RerankOutcome tryLlmRerank(String question, List<RetrievedChunk> candidates, int topN) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return null;
        }
        try {
            ChatResponse response = chatModel.call(new Prompt(buildRerankPrompt(question, candidates)));
            String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? null
                    : response.getResult().getOutput().getText();
            Map<Integer, Double> scores = parseScores(text);
            if (scores.size() != candidates.size()) {
                log.warn("LLM 精排输出不完整（候选 {} 条，解析到 {} 条），回退 BM25。原始输出: {}",
                        candidates.size(), scores.size(), abbreviate(text));
                return null;
            }
            List<RetrievedChunk> scored = new ArrayList<>(candidates);
            for (int i = 0; i < scored.size(); i++) {
                double raw = scores.getOrDefault(i, 0d);
                scored.get(i).setRerankScore(Math.max(0d, Math.min(10d, raw)) / 10d);
            }
            scored.sort(Comparator
                    .comparing(RetrievedChunk::getRerankScore, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(RetrievedChunk::getVectorScore, Comparator.nullsLast(Comparator.reverseOrder())));
            return new RerankOutcome(RerankOutcome.STRATEGY_LLM, head(scored, topN));
        } catch (RuntimeException ex) {
            // 模型超时、返回异常都走 BM25，不把失败包装成精排成功。
            log.warn("LLM 精排调用失败，回退 BM25: {}", ex.getMessage());
            return null;
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "<null>";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "…";
    }

    String buildRerankPrompt(String question, List<RetrievedChunk> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是检索结果重排器。只输出分数，不要回答问题，不要解释。\n")
                .append("评分标准：10 分表示该片段能直接、完整地回答用户问题；0 分表示完全无关。\n")
                .append("输出要求：为下面每一个候选各输出一行「编号:分数」，分数是 0-10 的整数，不能遗漏。\n\n")
                .append("【用户问题】\n").append(question).append("\n\n")
                .append("【候选片段】\n");
        for (int i = 0; i < candidates.size(); i++) {
            prompt.append('[').append(i + 1).append("] ")
                    .append(snippet(candidates.get(i).getContent()))
                    .append('\n');
        }
        return prompt.toString();
    }

    Map<Integer, Double> parseScores(String text) {
        Map<Integer, Double> scores = new HashMap<>();
        if (!StringUtils.hasText(text)) {
            return scores;
        }
        Matcher matcher = SCORE_LINE.matcher(text);
        while (matcher.find()) {
            int index;
            try {
                index = Integer.parseInt(matcher.group(1)) - 1;
            } catch (NumberFormatException ex) {
                continue;
            }
            double score;
            try {
                score = Double.parseDouble(matcher.group(2));
            } catch (NumberFormatException ex) {
                continue;
            }
            scores.putIfAbsent(index, score);
        }
        return scores;
    }

    private RerankOutcome bm25(String question, List<RetrievedChunk> candidates, int topN) {
        List<String> queryTokens = tokenize(question);
        List<List<String>> documentTokens = new ArrayList<>(candidates.size());
        double totalLength = 0;
        for (RetrievedChunk chunk : candidates) {
            List<String> tokens = tokenize(chunk.getContent());
            documentTokens.add(tokens);
            totalLength += tokens.size();
        }
        double averageLength = candidates.isEmpty() ? 0 : totalLength / candidates.size();

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (List<String> tokens : documentTokens) {
            Set<String> distinct = new HashSet<>(tokens);
            for (String token : distinct) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }

        double maxScore = 0;
        List<Double> rawScores = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            List<String> tokens = documentTokens.get(i);
            Map<String, Integer> termFrequency = new HashMap<>();
            for (String token : tokens) {
                termFrequency.merge(token, 1, Integer::sum);
            }
            double score = 0;
            for (String token : queryTokens) {
                Integer tf = termFrequency.get(token);
                if (tf == null) {
                    continue;
                }
                int df = documentFrequency.getOrDefault(token, 0);
                int total = candidates.size();
                double idf = Math.log(1 + (total - df + 0.5) / (df + 0.5));
                double lengthRatio = averageLength == 0 ? 0 : tokens.size() / averageLength;
                double denominator = tf + BM25_K1 * (1 - BM25_B + BM25_B * lengthRatio);
                score += idf * (tf * (BM25_K1 + 1)) / denominator;
            }
            rawScores.add(score);
            maxScore = Math.max(maxScore, score);
        }

        List<RetrievedChunk> scored = new ArrayList<>(candidates);
        for (int i = 0; i < scored.size(); i++) {
            double normalized = maxScore <= 0 ? 0d : rawScores.get(i) / maxScore;
            scored.get(i).setRerankScore(round(normalized));
        }
        scored.sort(Comparator
                .comparing(RetrievedChunk::getRerankScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RetrievedChunk::getVectorScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return new RerankOutcome(RerankOutcome.STRATEGY_BM25, head(scored, topN));
    }

    /**
     * 中文按 2 字 shingle 切分（与 MySQL ngram 默认 2 字口径一致），英文/数字按单词切分。
     */
    static List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder cjkRun = new StringBuilder();
        StringBuilder latinRun = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (isCjk(c)) {
                flushLatin(latinRun, tokens);
                cjkRun.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjkRun, tokens);
                latinRun.append(c);
            } else {
                flushLatin(latinRun, tokens);
                flushCjk(cjkRun, tokens);
            }
        }
        flushLatin(latinRun, tokens);
        flushCjk(cjkRun, tokens);
        return new ArrayList<>(new LinkedHashSet<>(tokens));
    }

    private static void flushLatin(StringBuilder latinRun, List<String> tokens) {
        if (latinRun.length() == 0) {
            return;
        }
        String word = latinRun.toString();
        latinRun.setLength(0);
        if (word.length() > 1 && !STOP_WORDS.contains(word)) {
            tokens.add(word);
        }
    }

    private static void flushCjk(StringBuilder cjkRun, List<String> tokens) {
        if (cjkRun.length() == 0) {
            return;
        }
        String run = cjkRun.toString();
        cjkRun.setLength(0);
        if (run.length() == 1) {
            if (!STOP_WORDS.contains(run)) {
                tokens.add(run);
            }
            return;
        }
        for (int i = 0; i + 1 < run.length(); i++) {
            String shingle = run.substring(i, i + 2);
            if (!STOP_WORDS.contains(shingle)) {
                tokens.add(shingle);
            }
        }
    }

    private static boolean isCjk(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    private static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.replace('\n', ' ').trim();
        return trimmed.length() <= SNIPPET_LIMIT ? trimmed : trimmed.substring(0, SNIPPET_LIMIT) + "…";
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private static List<RetrievedChunk> head(List<RetrievedChunk> chunks, int topN) {
        if (chunks.size() <= topN) {
            return chunks;
        }
        return new ArrayList<>(chunks.subList(0, topN));
    }
}
