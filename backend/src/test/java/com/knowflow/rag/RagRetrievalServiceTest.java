package com.knowflow.rag;

import com.knowflow.config.RagProperties;
import com.knowflow.entity.RetrievableChunk;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.rag.vector.VectorIndex;
import com.knowflow.rag.vector.VectorIndexRouter;
import com.knowflow.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 向量层异常时的降级行为：Redis 连接失败 / FT.SEARCH 报错都要落到 MySQL FULLTEXT，不能把异常抛给调用方。
 */
@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorIndexRouter vectorIndexRouter;
    @Mock
    private VectorIndex vectorIndex;
    @Mock
    private DocumentChunkMapper documentChunkMapper;
    @Mock
    private RerankService rerankService;

    @Test
    void degradesToFulltextWhenVectorSearchThrows() {
        RagProperties properties = new RagProperties();
        RagRetrievalService service = new RagRetrievalService(
                embeddingService, vectorIndexRouter, documentChunkMapper, rerankService, properties);

        when(embeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(embeddingService.modelName()).thenReturn("bge-m3");
        when(vectorIndexRouter.active()).thenReturn(vectorIndex);
        when(vectorIndexRouter.type()).thenReturn("redis-stack");
        when(vectorIndex.search(anyLong(), anyString(), any(float[].class), anyInt()))
                .thenThrow(new com.knowflow.rag.redis.RedisCommandException("Redis 连接失败"));

        RetrievableChunk row = new RetrievableChunk();
        row.setId(11L);
        row.setDocumentId(4L);
        row.setOriginalFilename("acceptance-refund-policy.txt");
        row.setChunkIndex(0);
        row.setContent("退款需要提供购买凭证、序列号照片等四项材料。");
        when(documentChunkMapper.searchFulltext(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(row));

        RetrievedChunk reranked = new RetrievedChunk(
                11L, 4L, "acceptance-refund-policy.txt", 0,
                "退款需要提供购买凭证、序列号照片等四项材料。", null);
        reranked.setRerankScore(0.8d);
        when(rerankService.rerank(anyString(), any(), anyInt()))
                .thenReturn(new RerankOutcome(RerankOutcome.STRATEGY_BM25, List.of(reranked)));

        RetrievalOutcome outcome = service.retrieve(6L, "退款需要什么材料？");

        assertThat(outcome.isDegraded()).isTrue();
        assertThat(outcome.getDegradeReason()).isEqualTo("VECTOR_UNAVAILABLE");
        assertThat(outcome.getRetrievalMode()).isEqualTo(RetrievalOutcome.MODE_FULLTEXT);
        assertThat(outcome.getRerankStrategy()).isEqualTo(RerankOutcome.STRATEGY_BM25);
        assertThat(outcome.getChunks()).hasSize(1);
        assertThat(outcome.getChunks().get(0).getChunkId()).isEqualTo(11L);
    }
}
