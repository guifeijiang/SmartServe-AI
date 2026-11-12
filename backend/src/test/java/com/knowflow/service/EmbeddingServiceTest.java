package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
        embeddingService = new EmbeddingService(embeddingModelProvider, new ObjectMapper(), "text-embedding-v3");
    }

    @Test
    void embedReturnsVectorFromModel() {
        when(embeddingModel.embed("退款流程")).thenReturn(new float[] {0.2f, 0.8f});

        float[] vector = embeddingService.embed("退款流程");

        assertArrayEquals(new float[] {0.2f, 0.8f}, vector, 0.0001f);
        assertArrayEquals(vector, embeddingService.fromJson(embeddingService.toJson(vector)), 0.0001f);
        verify(embeddingModel).embed("退款流程");
    }

    @Test
    void embedDoesNotCallModelWhenUnconfigured() {
        when(embeddingModelProvider.getIfAvailable()).thenReturn(null);
        embeddingService = new EmbeddingService(embeddingModelProvider, new ObjectMapper(), "text-embedding-v3");

        assertThrows(AiServiceException.class, () -> embeddingService.embed("退款流程"));
        verify(embeddingModel, never()).embed("退款流程");
    }
}
