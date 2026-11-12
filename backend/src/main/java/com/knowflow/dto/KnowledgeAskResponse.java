package com.knowflow.dto;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeAskResponse {

    private Long sessionId;
    private String answer;
    private List<KnowledgeCitation> citations = new ArrayList<>();
    private RetrievalDiagnostics diagnostics;

    public KnowledgeAskResponse() {
    }

    public KnowledgeAskResponse(String answer, List<KnowledgeCitation> citations) {
        this(null, answer, citations);
    }

    public KnowledgeAskResponse(Long sessionId, String answer, List<KnowledgeCitation> citations) {
        this.sessionId = sessionId;
        this.answer = answer;
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<KnowledgeCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<KnowledgeCitation> citations) {
        this.citations = citations;
    }

    public RetrievalDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(RetrievalDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }
}
