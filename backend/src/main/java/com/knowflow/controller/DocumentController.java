package com.knowflow.controller;

import com.knowflow.dto.DocumentResponse;
import com.knowflow.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            Authentication authentication,
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file
    ) {
        return documentService.upload(currentUserId(authentication), knowledgeBaseId, file);
    }

    @GetMapping
    public List<DocumentResponse> list(Authentication authentication, @PathVariable Long knowledgeBaseId) {
        return documentService.list(currentUserId(authentication), knowledgeBaseId);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(
            Authentication authentication,
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId
    ) {
        return documentService.get(currentUserId(authentication), knowledgeBaseId, documentId);
    }

    @PostMapping("/{documentId}/process")
    public DocumentResponse process(
            Authentication authentication,
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId
    ) {
        return documentService.process(currentUserId(authentication), knowledgeBaseId, documentId);
    }

    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            Authentication authentication,
            @PathVariable Long knowledgeBaseId,
            @PathVariable Long documentId
    ) {
        documentService.delete(currentUserId(authentication), knowledgeBaseId, documentId);
    }

    private Long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
