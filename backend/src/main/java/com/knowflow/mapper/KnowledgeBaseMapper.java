package com.knowflow.mapper;

import com.knowflow.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {

    int insert(KnowledgeBase knowledgeBase);

    KnowledgeBase findActiveById(@Param("id") Long id);

    List<KnowledgeBase> findActiveByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    int updateByIdAndOwner(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("name") String name,
            @Param("description") String description
    );

    int softDeleteByIdAndOwner(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);
}
