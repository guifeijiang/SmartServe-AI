package com.knowflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageResponse {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private List<Long> citedChunkIds = new ArrayList<>();
    private RetrievalDiagnostics retrieval;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Long> getCitedChunkIds() {
        return citedChunkIds;
    }

    public void setCitedChunkIds(List<Long> citedChunkIds) {
        this.citedChunkIds = citedChunkIds == null ? new ArrayList<>() : citedChunkIds;
    }

    public RetrievalDiagnostics getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(RetrievalDiagnostics retrieval) {
        this.retrieval = retrieval;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
