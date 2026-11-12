package com.knowflow.service;

import com.knowflow.common.InvalidRequestException;
import com.knowflow.common.ResourceNotFoundException;
import com.knowflow.dto.DocumentResponse;
import com.knowflow.entity.DocumentChunk;
import com.knowflow.entity.KbDocument;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.mapper.KbDocumentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 负责文档落盘、元数据、同步解析分块，以及文本块向量生成。
 */
@Service
public class DocumentService {

    static final String STATUS_UPLOADED = "UPLOADED";
    static final String STATUS_PARSING = "PARSING";
    static final String STATUS_READY = "READY";
    static final String STATUS_FAILED = "FAILED";
    static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".txt", ".pdf", ".docx");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".txt", "text/plain",
            ".pdf", "application/pdf",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbDocumentMapper kbDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final EmbeddingService embeddingService;
    private final TextExtractor textExtractor;
    private final TextChunker textChunker;
    private final VectorIndexSyncService vectorIndexSyncService;
    private final TransactionTemplate transactionTemplate;
    private final Path storageRoot;

    public DocumentService(
            KnowledgeBaseService knowledgeBaseService,
            KbDocumentMapper kbDocumentMapper,
            DocumentChunkMapper documentChunkMapper,
            EmbeddingService embeddingService,
            VectorIndexSyncService vectorIndexSyncService,
            PlatformTransactionManager transactionManager,
            @Value("${knowflow.storage.local-dir}") String storageDir
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbDocumentMapper = kbDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.embeddingService = embeddingService;
        this.vectorIndexSyncService = vectorIndexSyncService;
        this.textExtractor = new TextExtractor();
        this.textChunker = new TextChunker();
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Transactional
    public DocumentResponse upload(Long userId, Long knowledgeBaseId, MultipartFile file) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        validateFile(file);

        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        String storedName = UUID.randomUUID() + extension;
        Path relativePath = Path.of(String.valueOf(knowledgeBaseId), storedName);
        Path target = storageRoot.resolve(relativePath).normalize();
        ensureInsideStorageRoot(target);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new IllegalStateException("保存文件失败", ex);
        }

        KbDocument document = new KbDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setUploaderUserId(userId);
        document.setOriginalFilename(originalFilename);
        document.setStoragePath(relativePath.toString().replace('\\', '/'));
        document.setContentType(CONTENT_TYPES.get(extension));
        document.setFileSize(file.getSize());
        document.setProcessStatus(STATUS_UPLOADED);
        document.setChunkCount(0);
        try {
            kbDocumentMapper.insert(document);
        } catch (RuntimeException ex) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 元数据写入失败时尽量清掉已落盘文件，避免垃圾文件。
            }
            throw ex;
        }
        return toResponse(document);
    }

    public List<DocumentResponse> list(Long userId, Long knowledgeBaseId) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        return kbDocumentMapper.findActiveByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    public DocumentResponse get(Long userId, Long knowledgeBaseId, Long documentId) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        return toResponse(requireInKnowledgeBase(knowledgeBaseId, documentId));
    }

    /**
     * 先在事务外抽文本并生成全部向量，成功后再用短事务替换旧分块。
     */
    public DocumentResponse process(Long userId, Long knowledgeBaseId, Long documentId) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        KbDocument document = requireInKnowledgeBase(knowledgeBaseId, documentId);
        markStatus(document.getId(), STATUS_PARSING, null, document.getChunkCount());
        try {
            Path file = storageRoot.resolve(document.getStoragePath()).normalize();
            ensureInsideStorageRoot(file);
            if (!Files.exists(file)) {
                throw new IOException("找不到已上传的文件");
            }
            String text = textExtractor.extract(file, document.getOriginalFilename());
            List<String> chunks = textChunker.split(text);
            if (chunks.isEmpty()) {
                throw new IOException("未能提取到文本");
            }
            List<float[]> embeddings = new ArrayList<>();
            for (String content : chunks) {
                embeddings.add(embeddingService.embed(content));
            }
            DocumentResponse response = replaceChunks(document, chunks, embeddings);
            // MySQL 提交后再同步向量索引：同步失败不会回滚文档处理，MySQL 里的向量仍可用于重建。
            vectorIndexSyncService.syncDocument(document.getId());
            return response;
        } catch (Exception ex) {
            return markFailed(document, ex.getMessage());
        }
    }

    private DocumentResponse replaceChunks(KbDocument document, List<String> chunks, List<float[]> embeddings) {
        return transactionTemplate.execute(status -> {
            documentChunkMapper.deleteByDocumentId(document.getId());
            for (int index = 0; index < chunks.size(); index++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(index);
                chunk.setContent(chunks.get(index));
                chunk.setTokenCount(chunks.get(index).length());
                chunk.setEmbeddingJson(embeddingService.toJson(embeddings.get(index)));
                chunk.setEmbeddingModel(embeddingService.modelName());
                chunk.setEmbeddingDim(embeddings.get(index).length);
                documentChunkMapper.insert(chunk);
            }
            kbDocumentMapper.updateProcessResult(document.getId(), STATUS_READY, null, chunks.size());
            document.setProcessStatus(STATUS_READY);
            document.setProcessError(null);
            document.setChunkCount(chunks.size());
            return toResponse(document);
        });
    }

    private DocumentResponse markFailed(KbDocument document, String message) {
        String error = truncateError(message);
        markStatus(document.getId(), STATUS_FAILED, error, document.getChunkCount());
        document.setProcessStatus(STATUS_FAILED);
        document.setProcessError(error);
        return toResponse(document);
    }

    private void markStatus(Long documentId, String status, String error, Integer chunkCount) {
        transactionTemplate.executeWithoutResult(tx ->
                kbDocumentMapper.updateProcessResult(documentId, status, error, chunkCount)
        );
    }

    @Transactional
    public void delete(Long userId, Long knowledgeBaseId, Long documentId) {
        knowledgeBaseService.getMine(userId, knowledgeBaseId);
        KbDocument document = requireInKnowledgeBase(knowledgeBaseId, documentId);
        kbDocumentMapper.softDeleteById(document.getId());
        documentChunkMapper.deleteByDocumentId(document.getId());
        vectorIndexSyncService.removeDocument(documentId);
        Path target = storageRoot.resolve(document.getStoragePath()).normalize();
        ensureInsideStorageRoot(target);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("删除本地文件失败", ex);
        }
    }

    private KbDocument requireInKnowledgeBase(Long knowledgeBaseId, Long documentId) {
        KbDocument document = kbDocumentMapper.findActiveById(documentId);
        if (document == null || !knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
            throw new ResourceNotFoundException("文档不存在");
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidRequestException("文件大小不能超过 20MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidRequestException("文件名无效");
        }
        String extension = extensionOf(safeOriginalFilename(originalFilename));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidRequestException("仅支持 TXT、PDF、DOCX");
        }
    }

    private String safeOriginalFilename(String originalFilename) {
        String name = Path.of(originalFilename).getFileName().toString();
        if (name.length() > 255) {
            return name.substring(name.length() - 255);
        }
        return name;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private void ensureInsideStorageRoot(Path target) {
        if (!target.startsWith(storageRoot)) {
            throw new InvalidRequestException("非法文件路径");
        }
    }

    private String truncateError(String message) {
        if (message == null || message.isBlank()) {
            return "文档处理失败";
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    private DocumentResponse toResponse(KbDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getKnowledgeBaseId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getProcessStatus(),
                document.getProcessError(),
                document.getChunkCount(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
