package com.knowflow.controller;

import com.knowflow.entity.KnowledgeBase;
import com.knowflow.entity.SysUser;
import com.knowflow.mapper.KbDocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private SysUserMapper sysUserMapper;

    @MockitoBean
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @MockitoBean
    private KbDocumentMapper kbDocumentMapper;

    @Test
    void uploadRequiresLogin() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes()
        );
        mockMvc.perform(multipart("/api/knowledge-bases/10/documents").file(file))
                .andExpect(status().isUnauthorized());
        verify(kbDocumentMapper, never()).insert(any());
    }

    @Test
    void uploadSucceedsForOwnedKnowledgeBase() throws Exception {
        when(sysUserMapper.findByUsername("knowflow")).thenReturn(user(1L));
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(10L);
        kb.setOwnerUserId(1L);
        kb.setName("产品手册");
        kb.setStatus("ACTIVE");
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(kb);
        when(kbDocumentMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, com.knowflow.entity.KbDocument.class).setId(99L);
            return 1;
        });

        String token = loginToken();
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes()
        );
        mockMvc.perform(multipart("/api/knowledge-bases/10/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("notes.txt"))
                .andExpect(jsonPath("$.processStatus").value("UPLOADED"));
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
