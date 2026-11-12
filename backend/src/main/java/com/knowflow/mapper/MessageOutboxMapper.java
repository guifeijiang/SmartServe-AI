package com.knowflow.mapper;

import com.knowflow.entity.MessageOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageOutboxMapper {

    int insert(MessageOutbox outbox);

    List<MessageOutbox> findDuePending(@Param("limit") int limit);

    int markSent(@Param("id") Long id);

    int markRetry(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("nextRetryAt") LocalDateTime nextRetryAt
    );

    MessageOutbox findById(@Param("id") Long id);

    MessageOutbox findByEventId(@Param("eventId") String eventId);
}
