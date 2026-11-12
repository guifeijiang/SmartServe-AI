package com.knowflow.mapper;

import com.knowflow.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {

    int insert(ChatMessage message);

    List<ChatMessage> findBySessionId(@Param("sessionId") Long sessionId);

    List<ChatMessage> findRecentBySessionId(@Param("sessionId") Long sessionId, @Param("limit") int limit);
}
