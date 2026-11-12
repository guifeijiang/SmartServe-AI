package com.knowflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekChatModelTest {

    private MockRestServiceServer server;
    private DeepSeekChatModel model;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.deepseek.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        model = new DeepSeekChatModel(builder.build(), new ObjectMapper(), "deepseek-chat", 0.2);
    }

    @Test
    void callSendsPromptAndParsesAnswer() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("你好"))
                .andExpect(jsonPath("$.stream").value(false))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好，我是助手\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        ChatResponse response = model.call(new Prompt("你好"));

        assertEquals("你好，我是助手", response.getResult().getOutput().getText());
        server.verify();
    }

    @Test
    void callMapsSystemAndUserMessages() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        model.call(new Prompt(new SystemMessage("你是助手"), new UserMessage("问题")));

        server.verify();
    }

    @Test
    void callExposesApiErrorWithoutNetworkRetry() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"invalid api key\"}}"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> model.call(new Prompt("你好"))
        );

        assertTrue(ex.getMessage().contains("401"));
        assertTrue(ex.getMessage().contains("invalid api key"));
        server.verify();
    }
}
