package com.knowflow.controller;

import com.knowflow.entity.KnowledgeBase;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
class KnowledgeBaseMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private SysUserMapper sysUserMapper;

    @MockitoBean
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Test
    void createRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"产品手册\",\"description\":\"内部文档\"}"))
                .andExpect(status().isUnauthorized());
        verify(knowledgeBaseMapper, never()).insert(any());
    }

    @Test
    void createUsesJwtUserIdNotRequestBodyOwner() throws Exception {
        SysUser user = user(1L);
        when(sysUserMapper.findByUsername("knowflow")).thenReturn(user);
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase kb = invocation.getArgument(0);
            kb.setId(10L);
            return 1;
        });

        String token = loginToken();

        mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"产品手册\",\"description\":\"内部文档\",\"ownerUserId\":999}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("产品手册"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getOwnerUserId());
    }

    private String loginToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"knowflow\",\"password\":\"plain-pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return loginResult.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("knowflow");
        user.setPasswordHash(passwordEncoder.encode("plain-pass"));
        user.setDisplayName("knowflow");
        user.setStatus("ACTIVE");
        return user;
    }
}
