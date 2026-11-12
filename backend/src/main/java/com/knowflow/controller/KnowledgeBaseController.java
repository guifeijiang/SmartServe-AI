package com.knowflow.controller;

import com.knowflow.dto.AiChatRequest;
import com.knowflow.dto.ChatSessionResponse;
import com.knowflow.dto.CreateChatSessionRequest;
import com.knowflow.dto.KnowledgeAskResponse;
import com.knowflow.dto.KnowledgeBaseRequest;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.rag.vector.VectorIndexRouter;
import com.knowflow.service.ChatSessionService;
import com.knowflow.service.KnowledgeBaseService;
import com.knowflow.service.KnowledgeQaService;
import com.knowflow.service.VectorIndexSyncService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeQaService knowledgeQaService;
    private final ChatSessionService chatSessionService;
    private final VectorIndexSyncService vectorIndexSyncService;
    private final VectorIndexRouter vectorIndexRouter;

    public KnowledgeBaseController(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeQaService knowledgeQaService,
            ChatSessionService chatSessionService,
            VectorIndexSyncService vectorIndexSyncService,
            VectorIndexRouter vectorIndexRouter
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeQaService = knowledgeQaService;
        this.chatSessionService = chatSessionService;
        this.vectorIndexSyncService = vectorIndexSyncService;
        this.vectorIndexRouter = vectorIndexRouter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeBaseResponse create(Authentication authentication, @Valid @RequestBody KnowledgeBaseRequest request) {
        return knowledgeBaseService.create(currentUserId(authentication), request);
    }

    @GetMapping
    public List<KnowledgeBaseResponse> list(Authentication authentication) {
        return knowledgeBaseService.listMine(currentUserId(authentication));
    }

    @GetMapping("/{id}")
    public KnowledgeBaseResponse get(Authentication authentication, @PathVariable Long id) {
        return knowledgeBaseService.getMine(currentUserId(authentication), id);
    }

    @PutMapping("/{id}")
    public KnowledgeBaseResponse update(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeBaseRequest request
    ) {
        return knowledgeBaseService.updateMine(currentUserId(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long id) {
        knowledgeBaseService.deleteMine(currentUserId(authentication), id);
    }

    @PostMapping("/{id}/ask")
    public KnowledgeAskResponse ask(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request
    ) {
        return knowledgeQaService.ask(
                currentUserId(authentication),
                id,
                request.getMessage(),
                request.getSessionId()
        );
    }

    @PostMapping("/{id}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse createSession(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CreateChatSessionRequest request
    ) {
        return chatSessionService.create(
                currentUserId(authentication),
                id,
                request == null ? new CreateChatSessionRequest() : request
        );
    }

    @GetMapping("/{id}/sessions")
    public List<ChatSessionResponse> listSessions(Authentication authentication, @PathVariable Long id) {
        return chatSessionService.listMine(currentUserId(authentication), id);
    }

    /**
     * 用 MySQL 中已有的分块向量重建向量索引（不重新调用 Embedding，不产生调用费用）。
     */
    @PostMapping("/{id}/reindex")
    public java.util.Map<String, Object> reindex(Authentication authentication, @PathVariable Long id) {
        knowledgeBaseService.getMine(currentUserId(authentication), id);
        int indexed = vectorIndexSyncService.rebuild(id);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("knowledgeBaseId", id);
        result.put("indexType", vectorIndexRouter.type());
        result.put("indexedChunks", indexed);
        result.put("reason", vectorIndexRouter.selectionReason());
        return result;
    }

    private Long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
