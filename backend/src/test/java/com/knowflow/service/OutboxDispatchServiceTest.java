package com.knowflow.service;

import com.knowflow.config.RocketMqProperties;
import com.knowflow.entity.MessageOutbox;
import com.knowflow.mapper.MessageOutboxMapper;
import com.knowflow.mq.RocketMqMessageSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatchServiceTest {

    @Mock
    private MessageOutboxMapper messageOutboxMapper;

    @Mock
    private RocketMqMessageSender rocketMqMessageSender;

    private OutboxDispatchService outboxDispatchService;

    @BeforeEach
    void setUp() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.setMaxRetry(5);
        properties.setDispatchBatchSize(20);
        outboxDispatchService = new OutboxDispatchService(messageOutboxMapper, rocketMqMessageSender, properties);
    }

    @Test
    void dispatchMarksSentAfterSuccessfulSend() {
        MessageOutbox outbox = pending(1L, 0);
        when(messageOutboxMapper.findDuePending(20)).thenReturn(List.of(outbox));

        int sent = outboxDispatchService.dispatchDueMessages();

        assertEquals(1, sent);
        verify(rocketMqMessageSender).send("TICKET_CREATED", "CREATED", "evt-1", "{\"ticketId\":100}");
        verify(messageOutboxMapper).markSent(1L);
        verify(messageOutboxMapper, never()).markRetry(anyLong(), any(), anyInt(), any(), any());
    }

    @Test
    void dispatchRetriesAndRecordsFailureReason() {
        MessageOutbox outbox = pending(2L, 1);
        when(messageOutboxMapper.findDuePending(20)).thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("broker unavailable")).when(rocketMqMessageSender)
                .send(any(), any(), any(), any());

        int sent = outboxDispatchService.dispatchDueMessages();

        assertEquals(0, sent);
        verify(messageOutboxMapper, never()).markSent(2L);
        ArgumentCaptor<LocalDateTime> nextRetry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(messageOutboxMapper).markRetry(
                eq(2L),
                eq(MessageOutbox.STATUS_PENDING),
                eq(2),
                eq("broker unavailable"),
                nextRetry.capture()
        );
        assertNotNull(nextRetry.getValue());
    }

    @Test
    void dispatchMarksFailedWhenRetryExhausted() {
        MessageOutbox outbox = pending(3L, 4);
        when(messageOutboxMapper.findDuePending(20)).thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("timeout")).when(rocketMqMessageSender)
                .send(any(), any(), any(), any());

        outboxDispatchService.dispatchDueMessages();

        verify(messageOutboxMapper).markRetry(
                eq(3L),
                eq(MessageOutbox.STATUS_FAILED),
                eq(5),
                eq("timeout"),
                isNull()
        );
    }

    private static MessageOutbox pending(Long id, int retryCount) {
        MessageOutbox outbox = new MessageOutbox();
        outbox.setId(id);
        outbox.setEventId("evt-" + id);
        outbox.setTopic("TICKET_CREATED");
        outbox.setTag("CREATED");
        outbox.setPayload("{\"ticketId\":100}");
        outbox.setStatus(MessageOutbox.STATUS_PENDING);
        outbox.setRetryCount(retryCount);
        return outbox;
    }
}
