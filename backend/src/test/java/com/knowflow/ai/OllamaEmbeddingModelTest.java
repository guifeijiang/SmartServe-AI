package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaEmbeddingModelTest {

    private MockRestServiceServer server;
    private OllamaEmbeddingModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        model = new OllamaEmbeddingModel(builder.build(), new ObjectMapper(), "bge-m3", 1024);
    }

    @Test
    void embedUsesBgeM3AndParsesVector() {
        server.expect(requestTo("http://127.0.0.1:11434/api/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("退款流程"))
                .andRespond(withSuccess(
                        "{\"embeddings\":[[0.1,0.2,0.3]]}",
                        MediaType.APPLICATION_JSON
                ));

        float[] vector = model.embed("退款流程");

        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f}, vector, 0.0001f);
        assertEquals(1024, model.dimensions());
        server.verify();
    }

    @Test
    void embedBatchKeepsInputOrder() {
        server.expect(requestTo("http://127.0.0.1:11434/api/embed"))
                .andExpect(jsonPath("$.input[0]").value("甲"))
                .andExpect(jsonPath("$.input[1]").value("乙"))
                .andRespond(withSuccess(
                        "{\"embeddings\":[[1.0,0.0],[0.0,1.0]]}",
                        MediaType.APPLICATION_JSON
                ));

        List<float[]> vectors = model.embed(List.of("甲", "乙"));

        assertEquals(2, vectors.size());
        assertArrayEquals(new float[] {1f, 0f}, vectors.get(0), 0.0001f);
        assertArrayEquals(new float[] {0f, 1f}, vectors.get(1), 0.0001f);
        server.verify();
    }

    @Test
    void missingBgeM3ProducesClearError() {
        server.expect(requestTo("http://127.0.0.1:11434/api/embed"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model \\\"bge-m3\\\" not found\"}"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> model.embed("退款流程")
        );

        assertTrue(ex.getMessage().contains("404"));
        assertTrue(ex.getMessage().contains("bge-m3"));
        server.verify();
    }
}
