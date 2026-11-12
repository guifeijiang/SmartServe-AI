package com.knowflow.service;

import com.knowflow.common.ForbiddenException;
import com.knowflow.common.InvalidRequestException;
import com.knowflow.dto.DocumentResponse;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.entity.DocumentChunk;
import com.knowflow.entity.KbDocument;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.mapper.KbDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long KB_ID = 10L;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KbDocumentMapper kbDocumentMapper;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private VectorIndexSyncService vectorIndexSyncService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @TempDir
    Path tempDir;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        documentService = new DocumentService(
                knowledgeBaseService,
                kbDocumentMapper,
                documentChunkMapper,
                embeddingService,
                vectorIndexSyncService,
                transactionManager,
                tempDir.toString()
        );
    }

    @Test
    void uploadSavesFileAndMetadata() throws Exception {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(kbDocumentMapper.insert(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(99L);
            return 1;
        });
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello knowflow".getBytes()
        );

        DocumentResponse response = documentService.upload(OWNER_ID, KB_ID, file);

        ArgumentCaptor<KbDocument> captor = ArgumentCaptor.forClass(KbDocument.class);
        verify(kbDocumentMapper).insert(captor.capture());
        KbDocument saved = captor.getValue();
        assertEquals(KB_ID, saved.getKnowledgeBaseId());
        assertEquals(OWNER_ID, saved.getUploaderUserId());
        assertEquals("notes.txt", saved.getOriginalFilename());
        assertEquals("text/plain", saved.getContentType());
        assertEquals("UPLOADED", saved.getProcessStatus());
        assertEquals(0, saved.getChunkCount());
        assertTrue(saved.getStoragePath().startsWith(KB_ID + "/"));
        assertTrue(saved.getStoragePath().endsWith(".txt"));
        assertTrue(Files.exists(tempDir.resolve(saved.getStoragePath())));
        assertEquals(99L, response.getId());
        assertEquals("UPLOADED", response.getProcessStatus());
    }

    @Test
    void uploadRejectsUnsupportedType() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[] {1, 2, 3}
        );

        assertThrows(InvalidRequestException.class, () -> documentService.upload(OWNER_ID, KB_ID, file));
        verify(kbDocumentMapper, never()).insert(any());
    }

    @Test
    void uploadRejectsFileLargerThan20Mb() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "huge.txt",
                "text/plain",
                new byte[(int) DocumentService.MAX_FILE_SIZE + 1]
        );

        InvalidRequestException ex = assertThrows(
                InvalidRequestException.class,
                () -> documentService.upload(OWNER_ID, KB_ID, file)
        );
        assertTrue(ex.getMessage().contains("20MB"));
        verify(kbDocumentMapper, never()).insert(any());
    }

    @Test
    void uploadRejectedWhenKnowledgeBaseNotOwned() {
        doThrow(new ForbiddenException("无权访问该知识库"))
                .when(knowledgeBaseService).getMine(OTHER_ID, KB_ID);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes()
        );

        assertThrows(ForbiddenException.class, () -> documentService.upload(OTHER_ID, KB_ID, file));
        verify(kbDocumentMapper, never()).insert(any());
    }

    @Test
    void listReturnsDocumentsOfOwnedKnowledgeBase() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(kbDocumentMapper.findActiveByKnowledgeBaseId(KB_ID)).thenReturn(List.of(sampleDocument()));

        List<DocumentResponse> list = documentService.list(OWNER_ID, KB_ID);

        assertEquals(1, list.size());
        assertEquals("notes.txt", list.get(0).getOriginalFilename());
    }

    @Test
    void processTxtSavesChunksAndMarksReady() throws Exception {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        stubSuccessfulEmbedding();
        KbDocument document = sampleDocument();
        Path stored = tempDir.resolve(document.getStoragePath());
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "第一段内容。\n\n第二段内容。");
        when(kbDocumentMapper.findActiveById(99L)).thenReturn(document);

        DocumentResponse response = documentService.process(OWNER_ID, KB_ID, 99L);

        assertEquals("READY", response.getProcessStatus());
        assertTrue(response.getChunkCount() >= 1);
        verify(embeddingService, org.mockito.Mockito.atLeastOnce()).embed(any());
        org.mockito.InOrder order = inOrder(documentChunkMapper, kbDocumentMapper);
        order.verify(documentChunkMapper).deleteByDocumentId(99L);
        ArgumentCaptor<DocumentChunk> chunkCaptor = ArgumentCaptor.forClass(DocumentChunk.class);
        order.verify(documentChunkMapper).insert(chunkCaptor.capture());
        assertEquals("[0.1,0.2]", chunkCaptor.getValue().getEmbeddingJson());
        assertEquals("text-embedding-v3", chunkCaptor.getValue().getEmbeddingModel());
        verify(kbDocumentMapper).updateProcessResult(eq(99L), eq("READY"), eq(null), anyInt());
        org.mockito.InOrder embedThenReplace = inOrder(embeddingService, documentChunkMapper);
        embedThenReplace.verify(embeddingService, org.mockito.Mockito.atLeastOnce()).embed(any());
        embedThenReplace.verify(documentChunkMapper).deleteByDocumentId(99L);
    }

    @Test
    void processFailsWhenEmbeddingFailsAndKeepsOldChunks() throws Exception {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(embeddingService.embed(any())).thenThrow(new com.knowflow.common.AiServiceException("向量生成失败"));
        KbDocument document = sampleDocument();
        Path stored = tempDir.resolve(document.getStoragePath());
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "第一段内容。");
        when(kbDocumentMapper.findActiveById(99L)).thenReturn(document);

        DocumentResponse response = documentService.process(OWNER_ID, KB_ID, 99L);

        assertEquals("FAILED", response.getProcessStatus());
        assertTrue(response.getProcessError().contains("向量生成失败"));
        verify(documentChunkMapper, never()).deleteByDocumentId(any());
        verify(documentChunkMapper, never()).insert(any());
        verify(kbDocumentMapper, never()).updateProcessResult(eq(99L), eq("READY"), eq(null), anyInt());
    }

    @Test
    void processReplacesOldChunks() throws Exception {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        stubSuccessfulEmbedding();
        KbDocument document = sampleDocument();
        Path stored = tempDir.resolve(document.getStoragePath());
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "可重复处理的文本");
        when(kbDocumentMapper.findActiveById(99L)).thenReturn(document);

        documentService.process(OWNER_ID, KB_ID, 99L);
        documentService.process(OWNER_ID, KB_ID, 99L);

        verify(documentChunkMapper, org.mockito.Mockito.times(2)).deleteByDocumentId(99L);
    }

    @Test
    void processMarksFailedWhenFileMissing() {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        when(kbDocumentMapper.findActiveById(99L)).thenReturn(sampleDocument());

        DocumentResponse response = documentService.process(OWNER_ID, KB_ID, 99L);

        assertEquals("FAILED", response.getProcessStatus());
        assertTrue(response.getProcessError().contains("找不到"));
        verify(documentChunkMapper, never()).deleteByDocumentId(any());
        verify(kbDocumentMapper).updateProcessResult(eq(99L), eq("FAILED"), any(), eq(0));
    }

    @Test
    void processRejectedWhenKnowledgeBaseNotOwned() {
        doThrow(new ForbiddenException("无权访问该知识库"))
                .when(knowledgeBaseService).getMine(OTHER_ID, KB_ID);

        assertThrows(ForbiddenException.class, () -> documentService.process(OTHER_ID, KB_ID, 99L));
        verify(documentChunkMapper, never()).insert(any());
    }

    @Test
    void deleteSoftDeletesAndRemovesLocalFile() throws Exception {
        when(knowledgeBaseService.getMine(OWNER_ID, KB_ID)).thenReturn(kbResponse());
        KbDocument document = sampleDocument();
        Path stored = tempDir.resolve(document.getStoragePath());
        Files.createDirectories(stored.getParent());
        Files.writeString(stored, "hello");
        when(kbDocumentMapper.findActiveById(99L)).thenReturn(document);

        documentService.delete(OWNER_ID, KB_ID, 99L);

        verify(kbDocumentMapper).softDeleteById(99L);
        assertFalse(Files.exists(stored));
    }

    @Test
    void deleteRejectedWhenKnowledgeBaseNotOwned() {
        doThrow(new ForbiddenException("无权访问该知识库"))
                .when(knowledgeBaseService).getMine(OTHER_ID, KB_ID);

        assertThrows(ForbiddenException.class, () -> documentService.delete(OTHER_ID, KB_ID, 99L));
        verify(kbDocumentMapper, never()).softDeleteById(any());
    }

    private void stubSuccessfulEmbedding() {
        when(embeddingService.embed(any())).thenReturn(new float[] {0.1f, 0.2f});
        when(embeddingService.toJson(any())).thenReturn("[0.1,0.2]");
        when(embeddingService.modelName()).thenReturn("text-embedding-v3");
    }

    private KnowledgeBaseResponse kbResponse() {
        return new KnowledgeBaseResponse(KB_ID, "产品手册", "内部文档", "ACTIVE", null, null);
    }

    private KbDocument sampleDocument() {
        KbDocument document = new KbDocument();
        document.setId(99L);
        document.setKnowledgeBaseId(KB_ID);
        document.setUploaderUserId(OWNER_ID);
        document.setOriginalFilename("notes.txt");
        document.setStoragePath(KB_ID + "/stored.txt");
        document.setContentType("text/plain");
        document.setFileSize(5L);
        document.setProcessStatus("UPLOADED");
        document.setChunkCount(0);
        return document;
    }
}
