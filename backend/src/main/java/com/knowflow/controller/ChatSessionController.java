package com.knowflow.controller;

import com.knowflow.dto.ChatMessageResponse;
import com.knowflow.service.ChatSessionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat-sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/{sessionId}/messages")
    public List<ChatMessageResponse> messages(Authentication authentication, @PathVariable Long sessionId) {
        return chatSessionService.listMessages((Long) authentication.getPrincipal(), sessionId);
    }
}
