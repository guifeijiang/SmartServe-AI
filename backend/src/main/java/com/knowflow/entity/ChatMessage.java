package com.knowflow.entity;

import java.time.LocalDateTime;

public class ChatMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String citedChunkIds;
    private String retrievalMeta;
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

    public String getCitedChunkIds() {
        return citedChunkIds;
    }

    public void setCitedChunkIds(String citedChunkIds) {
        this.citedChunkIds = citedChunkIds;
    }

    public String getRetrievalMeta() {
        return retrievalMeta;
    }

    public void setRetrievalMeta(String retrievalMeta) {
        this.retrievalMeta = retrievalMeta;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
