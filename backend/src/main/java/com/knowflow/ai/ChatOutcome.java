package com.knowflow.ai;

/**
 * 一次对话调用的结果分类，供 RAG 做三级降级：
 * ANSWERED = 正常回答；EMPTY = 模型返回空内容（用固定兜底话术）；FAILED = 调用异常/超时（提示转人工）。
 */
public class ChatOutcome {

    public enum Status {
        ANSWERED,
        EMPTY,
        FAILED
    }

    private final Status status;
    private final String answer;
    private final String error;

    private ChatOutcome(Status status, String answer, String error) {
        this.status = status;
        this.answer = answer;
        this.error = error;
    }

    public static ChatOutcome answered(String answer) {
        return new ChatOutcome(Status.ANSWERED, answer, null);
    }

    public static ChatOutcome empty() {
        return new ChatOutcome(Status.EMPTY, null, "模型未返回有效内容");
    }

    public static ChatOutcome failed(String error) {
        return new ChatOutcome(Status.FAILED, null, error);
    }

    public Status getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }

    public String getError() {
        return error;
    }

    public boolean isAnswered() {
        return status == Status.ANSWERED;
    }
}
