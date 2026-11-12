package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaChatModelTest {

    private MockRestServiceServer server;
    private OllamaChatModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        model = new OllamaChatModel(builder.build(), new ObjectMapper(), "deepseek-r1:1.5b", 0.2, 8192, 1024);
    }

    @Test
    void callSendsPromptAndParsesContent() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("deepseek-r1:1.5b"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("你好"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.num_ctx").value(8192))
                .andExpect(jsonPath("$.options.num_predict").value(1024))
                .andRespond(withSuccess(
                        "{\"model\":\"deepseek-r1:1.5b\",\"message\":{\"role\":\"assistant\",\"content\":\"\\n\\n你好\"},\"done\":true}",
                        MediaType.APPLICATION_JSON
                ));

        ChatResponse response = model.call(new Prompt("你好"));

        assertEquals("你好", response.getResult().getOutput().getText());
        server.verify();
    }

    @Test
    void callExposesModelNotFound() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model \\\"deepseek-r1:1.5b\\\" not found\"}"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> model.call(new Prompt("你好"))
        );

        assertTrue(ex.getMessage().contains("404"));
        assertTrue(ex.getMessage().contains("deepseek-r1:1.5b"));
        server.verify();
    }

    @Test
    void callRejectsThinkingOnlyResponse() {
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"thinking\":\"思考中\"},\"done\":true}",
                        MediaType.APPLICATION_JSON
                ));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> model.call(new Prompt("你好"))
        );

        assertTrue(ex.getMessage().contains("num-predict"));
        server.verify();
    }
}
