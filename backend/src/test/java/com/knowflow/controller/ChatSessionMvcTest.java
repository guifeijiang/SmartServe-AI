package com.knowflow.controller;

import com.knowflow.dto.ChatSessionResponse;
import com.knowflow.service.ChatSessionService;
import com.knowflow.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatSessionMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ChatSessionService chatSessionService;

    @Test
    void createSessionRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases/10/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verify(chatSessionService, never()).create(any(), any(), any());
    }

    @Test
    void createSessionReturnsCreated() throws Exception {
        ChatSessionResponse response = new ChatSessionResponse();
        response.setId(8L);
        response.setUserId(1L);
        response.setKnowledgeBaseId(10L);
        response.setTitle("新对话");
        when(chatSessionService.create(eq(1L), eq(10L), any())).thenReturn(response);

        mockMvc.perform(post("/api/knowledge-bases/10/sessions")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.knowledgeBaseId").value(10));
    }

    @Test
    void listMessagesRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/chat-sessions/8/messages"))
                .andExpect(status().isUnauthorized());
        verify(chatSessionService, never()).listMessages(any(), any());
    }

    @Test
    void listSessionsUsesCurrentUser() throws Exception {
        when(chatSessionService.listMine(1L, 10L)).thenReturn(List.of());

        mockMvc.perform(get("/api/knowledge-bases/10/sessions")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L)))
                .andExpect(status().isOk());
        verify(chatSessionService).listMine(1L, 10L);
    }
}
