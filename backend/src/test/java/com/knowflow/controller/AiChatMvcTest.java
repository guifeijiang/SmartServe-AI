package com.knowflow.controller;

import com.knowflow.entity.SysUser;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiChatMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private SysUserMapper sysUserMapper;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void chatRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void chatRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void chatReturnsAnswerFromMockedModel() throws Exception {
        when(sysUserMapper.findById(1L)).thenReturn(user(1L));
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("模拟回复"))))
        );

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"如何关闭工单？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("模拟回复"));
    }

    private static SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("ss_customer");
        user.setStatus("ACTIVE");
        user.setRole("CUSTOMER");
        return user;
    }
}
