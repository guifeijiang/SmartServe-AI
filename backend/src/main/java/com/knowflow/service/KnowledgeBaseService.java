package com.knowflow.service;

import com.knowflow.common.ForbiddenException;
import com.knowflow.common.ResourceNotFoundException;
import com.knowflow.dto.KnowledgeBaseRequest;
import com.knowflow.dto.KnowledgeBaseResponse;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.KnowledgeBaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库只属于创建者。ownerUserId 一律从 JWT 解析出的当前用户传入，禁止由请求体指定。
 */
@Service
public class KnowledgeBaseService {

    static final String STATUS_ACTIVE = "ACTIVE";

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Transactional
    public KnowledgeBaseResponse create(Long ownerUserId, KnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setOwnerUserId(ownerUserId);
        knowledgeBase.setName(request.getName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setStatus(STATUS_ACTIVE);
        knowledgeBaseMapper.insert(knowledgeBase);
        return toResponse(knowledgeBase);
    }

    public List<KnowledgeBaseResponse> listMine(Long ownerUserId) {
        return knowledgeBaseMapper.findActiveByOwnerUserId(ownerUserId).stream()
                .map(this::toResponse)
                .toList();
    }

    public KnowledgeBaseResponse getMine(Long ownerUserId, Long id) {
        return toResponse(requireOwned(ownerUserId, id));
    }

    @Transactional
    public KnowledgeBaseResponse updateMine(Long ownerUserId, Long id, KnowledgeBaseRequest request) {
        requireOwned(ownerUserId, id);
        knowledgeBaseMapper.updateByIdAndOwner(id, ownerUserId, request.getName(), request.getDescription());
        return toResponse(requireOwned(ownerUserId, id));
    }

    @Transactional
    public void deleteMine(Long ownerUserId, Long id) {
        requireOwned(ownerUserId, id);
        knowledgeBaseMapper.softDeleteByIdAndOwner(id, ownerUserId);
    }

    private KnowledgeBase requireOwned(Long ownerUserId, Long id) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.findActiveById(id);
        if (knowledgeBase == null) {
            throw new ResourceNotFoundException("知识库不存在");
        }
        if (!ownerUserId.equals(knowledgeBase.getOwnerUserId())) {
            throw new ForbiddenException("无权访问该知识库");
        }
        return knowledgeBase;
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getStatus(),
                knowledgeBase.getCreatedAt(),
                knowledgeBase.getUpdatedAt()
        );
    }
}
