package com.knowflow.mapper;

import com.knowflow.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {

    int insert(ChatSession session);

    ChatSession findActiveById(@Param("id") Long id);

    List<ChatSession> findActiveByUserIdAndKnowledgeBaseId(
            @Param("userId") Long userId,
            @Param("knowledgeBaseId") Long knowledgeBaseId
    );

    int touchUpdatedAt(@Param("id") Long id);

    int updateTitle(@Param("id") Long id, @Param("title") String title);
}
