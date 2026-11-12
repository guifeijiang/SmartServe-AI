package com.knowflow.service;

import com.knowflow.entity.RetrievableChunk;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.rag.vector.ChunkVector;
import com.knowflow.rag.vector.VectorIndex;
import com.knowflow.rag.vector.VectorIndexRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 向量同步的「先删后写」顺序、失败标记与 MySQL 降级路径。
 * 这些行为决定了 MySQL 分块与 Redis 向量是否会出现孤儿数据。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VectorIndexSyncServiceTest {

    @Mock
    private VectorIndexRouter vectorIndexRouter;
    @Mock
    private DocumentChunkMapper documentChunkMapper;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorIndex vectorIndex;

    private VectorIndexSyncService service;

    @BeforeEach
    void setUp() {
        service = new VectorIndexSyncService(vectorIndexRouter, documentChunkMapper, embeddingService);
        when(vectorIndexRouter.active()).thenReturn(vectorIndex);
        when(embeddingService.dimensions()).thenReturn(1024);
        when(embeddingService.modelName()).thenReturn("bge-m3");
    }

    @Test
    void syncDeletesOldVectorsBeforeWritingNewOnes() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByDocumentId(4L)).thenReturn(List.of(chunk(101L, 4L, 0)));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);

        service.syncDocument(4L);

        InOrder order = inOrder(vectorIndex, documentChunkMapper);
        order.verify(vectorIndex).deleteByDocument(4L);
        order.verify(vectorIndex).upsert(any());
        order.verify(documentChunkMapper).markVectorIndexState(4L, "SYNCED");

        ArgumentCaptor<List<ChunkVector>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorIndex).upsert(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getChunkId()).isEqualTo(101L);
        assertThat(captor.getValue().get(0).getEmbeddingModel()).isEqualTo("bge-m3");
        assertThat(captor.getValue().get(0).getContent()).isEqualTo("退款需要提供购买凭证等四项材料。");
    }

    @Test
    void syncMarksFailedWithoutBreakingDocumentProcessing() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByDocumentId(4L)).thenReturn(List.of(chunk(101L, 4L, 0)));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        doThrow(new IllegalStateException("Redis 连接失败")).when(vectorIndex).upsert(any());

        service.syncDocument(4L);

        verify(documentChunkMapper).markVectorIndexState(4L, "FAILED");
        verify(documentChunkMapper, never()).markVectorIndexState(4L, "SYNCED");
    }

    @Test
    void syncSkipsRedisCallsWhenIndexIsMysqlCosine() {
        when(vectorIndex.type()).thenReturn("mysql-cosine");

        service.syncDocument(4L);

        verify(vectorIndex, never()).deleteByDocument(anyLong());
        verify(vectorIndex, never()).upsert(any());
        verify(documentChunkMapper).markVectorIndexState(4L, "SYNCED");
    }

    @Test
    void rebuildClearsKnowledgeBaseVectorsBeforeUpserting() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(6L))
                .thenReturn(List.of(chunk(101L, 4L, 0), chunk(102L, 4L, 1), chunk(201L, 5L, 0)));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        when(vectorIndex.countByKnowledgeBase(6L)).thenReturn(3);

        int indexed = service.rebuild(6L);

        InOrder order = inOrder(vectorIndex, documentChunkMapper);
        order.verify(vectorIndex).deleteByKnowledgeBase(6L);
        order.verify(vectorIndex).upsert(any());
        verify(vectorIndex).countByKnowledgeBase(6L);
        verify(documentChunkMapper).markVectorIndexState(eq(4L), eq("SYNCED"));
        verify(documentChunkMapper).markVectorIndexState(eq(5L), eq("SYNCED"));
        assertThat(indexed).isEqualTo(3);
    }

    @Test
    void rebuildWritesInBatches() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(6L)).thenReturn(chunks(501, 4L));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        when(vectorIndex.countByKnowledgeBase(6L)).thenReturn(501);

        service.rebuild(6L);

        // 501 条按 500 一批切分：两批，不是一次性全写。
        verify(vectorIndex, times(2)).upsert(any());
    }

    @Test
    void rebuildMarksFailedWhenLaterBatchFails() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(6L)).thenReturn(chunks(501, 4L));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        doNothing().doThrow(new IllegalStateException("Redis 第二批写入失败")).when(vectorIndex).upsert(any());

        assertThatThrownBy(() -> service.rebuild(6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("向量重建失败")
                .hasMessageContaining("第二批写入失败");

        verify(vectorIndex, times(2)).upsert(any());
        verify(vectorIndex, never()).countByKnowledgeBase(anyLong());
        verify(documentChunkMapper).markVectorIndexState(4L, "FAILED");
        verify(documentChunkMapper, never()).markVectorIndexState(anyLong(), eq("SYNCED"));
    }

    @Test
    void rebuildFailsWhenStoredCountDoesNotMatchMysqlChunks() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(6L))
                .thenReturn(List.of(chunk(101L, 4L, 0), chunk(201L, 5L, 0)));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        when(vectorIndex.countByKnowledgeBase(6L)).thenReturn(1);

        assertThatThrownBy(() -> service.rebuild(6L))
                .hasMessageContaining("不一致");

        verify(documentChunkMapper).markVectorIndexState(4L, "FAILED");
        verify(documentChunkMapper).markVectorIndexState(5L, "FAILED");
        verify(documentChunkMapper, never()).markVectorIndexState(anyLong(), eq("SYNCED"));
    }

    @Test
    void rebuildCanBeRetriedAfterFailure() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(6L))
                .thenReturn(List.of(chunk(101L, 4L, 0), chunk(201L, 5L, 0)));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        when(vectorIndex.countByKnowledgeBase(6L)).thenReturn(2);
        doThrow(new IllegalStateException("Redis 暂时不可用")).doNothing().when(vectorIndex).upsert(any());

        assertThatThrownBy(() -> service.rebuild(6L)).hasMessageContaining("暂时不可用");
        int indexed = service.rebuild(6L);

        // 失败一次、重试一次：重试仍然先清空再写入，最终标 SYNCED，说明流程可重复执行。
        verify(vectorIndex, times(2)).deleteByKnowledgeBase(6L);
        verify(documentChunkMapper).markVectorIndexState(4L, "FAILED");
        verify(documentChunkMapper).markVectorIndexState(4L, "SYNCED");
        assertThat(indexed).isEqualTo(2);
    }

    @Test
    void rebuildIsIdempotentWhenRunTwice() {
        when(vectorIndex.type()).thenReturn("redis-stack");
        when(documentChunkMapper.findReadyByKnowledgeBaseId(6L))
                .thenReturn(List.of(chunk(101L, 4L, 0)));
        when(embeddingService.fromJson(anyString())).thenReturn(new float[1024]);
        when(vectorIndex.countByKnowledgeBase(6L)).thenReturn(1);

        assertThat(service.rebuild(6L)).isEqualTo(1);
        assertThat(service.rebuild(6L)).isEqualTo(1);

        verify(vectorIndex, times(2)).deleteByKnowledgeBase(6L);
        verify(vectorIndex, times(2)).upsert(any());
    }

    private List<RetrievableChunk> chunks(int count, Long documentId) {
        List<RetrievableChunk> chunks = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(chunk(1000L + i, documentId, i));
        }
        return chunks;
    }

    private RetrievableChunk chunk(Long id, Long documentId, Integer chunkIndex) {
        RetrievableChunk chunk = new RetrievableChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setKnowledgeBaseId(6L);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent("退款需要提供购买凭证等四项材料。");
        chunk.setEmbeddingModel("bge-m3");
        chunk.setEmbeddingJson("[0.1,0.2]");
        return chunk;
    }
}
