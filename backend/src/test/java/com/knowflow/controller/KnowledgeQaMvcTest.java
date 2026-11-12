package com.knowflow.controller;

import com.knowflow.dto.KnowledgeAskResponse;
import com.knowflow.service.JwtService;
import com.knowflow.service.KnowledgeQaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KnowledgeQaMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private KnowledgeQaService knowledgeQaService;

    @Test
    void askRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/10/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"如何退款？\"}"))
                .andExpect(status().isUnauthorized());
        verify(knowledgeQaService, never()).ask(eq(1L), eq(10L), eq("如何退款？"), eq(null));
    }

    @Test
    void askRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/10/ask")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(knowledgeQaService, never()).ask(eq(1L), eq(10L), eq(""), eq(null));
    }

    @Test
    void askReturnsAnswerAndCitations() throws Exception {
        KnowledgeAskResponse response = new KnowledgeAskResponse("根据手册办理退款", List.of());
        when(knowledgeQaService.ask(1L, 10L, "如何退款？", null)).thenReturn(response);

        mockMvc.perform(post("/api/knowledge-bases/10/ask")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"如何退款？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("根据手册办理退款"))
                .andExpect(jsonPath("$.citations").isArray());
    }

    @Test
    void askPassesSessionId() throws Exception {
        KnowledgeAskResponse response = new KnowledgeAskResponse(8L, "继续处理", List.of());
        when(knowledgeQaService.ask(1L, 10L, "然后呢？", 8L)).thenReturn(response);

        mockMvc.perform(post("/api/knowledge-bases/10/ask")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"然后呢？\",\"sessionId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(8))
                .andExpect(jsonPath("$.answer").value("继续处理"));
    }
}
