package com.knowflow.service;

import com.knowflow.common.ForbiddenException;
import com.knowflow.common.ResourceNotFoundException;
import com.knowflow.dto.KnowledgeBaseRequest;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.KnowledgeBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService(knowledgeBaseMapper);
    }

    @Test
    void createAssignsCurrentUserAndActiveStatus() {
        KnowledgeBaseRequest request = request("产品手册", "内部文档");
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase kb = invocation.getArgument(0);
            kb.setId(10L);
            return 1;
        });

        KnowledgeBaseResponse response = knowledgeBaseService.create(OWNER_ID, request);

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        KnowledgeBase saved = captor.getValue();
        assertEquals(OWNER_ID, saved.getOwnerUserId());
        assertEquals("产品手册", saved.getName());
        assertEquals("内部文档", saved.getDescription());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals(10L, response.getId());
        assertEquals("产品手册", response.getName());
    }

    @Test
    void listMineReturnsOnlyMapperResults() {
        when(knowledgeBaseMapper.findActiveByOwnerUserId(OWNER_ID)).thenReturn(List.of(ownedKb(10L)));

        List<KnowledgeBaseResponse> list = knowledgeBaseService.listMine(OWNER_ID);

        assertEquals(1, list.size());
        assertEquals(10L, list.get(0).getId());
        verify(knowledgeBaseMapper).findActiveByOwnerUserId(OWNER_ID);
    }

    @Test
    void getMineReturnsOwnedKnowledgeBase() {
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(ownedKb(10L));

        KnowledgeBaseResponse response = knowledgeBaseService.getMine(OWNER_ID, 10L);

        assertEquals("产品手册", response.getName());
    }

    @Test
    void getMineRejectsOtherUsersKnowledgeBase() {
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(ownedKb(10L));

        assertThrows(ForbiddenException.class, () -> knowledgeBaseService.getMine(OTHER_ID, 10L));
        verify(knowledgeBaseMapper, never()).updateByIdAndOwner(any(), any(), any(), any());
    }

    @Test
    void getMineRejectsMissingKnowledgeBase() {
        when(knowledgeBaseMapper.findActiveById(99L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> knowledgeBaseService.getMine(OWNER_ID, 99L));
    }

    @Test
    void updateMineChangesNameAndDescription() {
        KnowledgeBase original = ownedKb(10L);
        KnowledgeBase updated = ownedKb(10L);
        updated.setName("新名称");
        updated.setDescription("新描述");
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(original, updated);

        KnowledgeBaseResponse response = knowledgeBaseService.updateMine(OWNER_ID, 10L, request("新名称", "新描述"));

        verify(knowledgeBaseMapper).updateByIdAndOwner(10L, OWNER_ID, "新名称", "新描述");
        assertEquals("新名称", response.getName());
        assertEquals("新描述", response.getDescription());
    }

    @Test
    void updateMineRejectsOtherUsersKnowledgeBase() {
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(ownedKb(10L));

        assertThrows(
                ForbiddenException.class,
                () -> knowledgeBaseService.updateMine(OTHER_ID, 10L, request("黑客改名", "x"))
        );
        verify(knowledgeBaseMapper, never()).updateByIdAndOwner(any(), any(), any(), any());
    }

    @Test
    void deleteMineSoftDeletesOwnedKnowledgeBase() {
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(ownedKb(10L));

        knowledgeBaseService.deleteMine(OWNER_ID, 10L);

        verify(knowledgeBaseMapper).softDeleteByIdAndOwner(10L, OWNER_ID);
    }

    @Test
    void deleteMineRejectsOtherUsersKnowledgeBase() {
        when(knowledgeBaseMapper.findActiveById(10L)).thenReturn(ownedKb(10L));

        assertThrows(ForbiddenException.class, () -> knowledgeBaseService.deleteMine(OTHER_ID, 10L));
        verify(knowledgeBaseMapper, never()).softDeleteByIdAndOwner(eq(10L), eq(OTHER_ID));
        verify(knowledgeBaseMapper, never()).softDeleteByIdAndOwner(eq(10L), eq(OWNER_ID));
    }

    private KnowledgeBaseRequest request(String name, String description) {
        KnowledgeBaseRequest request = new KnowledgeBaseRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    private KnowledgeBase ownedKb(Long id) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(id);
        knowledgeBase.setOwnerUserId(OWNER_ID);
        knowledgeBase.setName("产品手册");
        knowledgeBase.setDescription("内部文档");
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBase.setCreatedAt(LocalDateTime.of(2026, 10, 30, 10, 0));
        knowledgeBase.setUpdatedAt(LocalDateTime.of(2026, 10, 30, 10, 0));
        knowledgeBase.setDeletedAt(null);
        return knowledgeBase;
    }
}
