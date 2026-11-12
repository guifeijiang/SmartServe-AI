package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.entity.TicketNotification;
import com.knowflow.mapper.TicketNotificationMapper;
import com.knowflow.mq.TicketCreatedMqListener;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCreatedEventHandlerTest {

    private static final String PAYLOAD = """
            {"eventId":"evt-1","ticketId":100,"ticketNo":"TKT-ABC","customerUserId":1,"title":"打印机无法出纸"}
            """;

    @Mock
    private TicketNotificationMapper ticketNotificationMapper;

    private TicketCreatedEventHandler handler;
    private TicketCreatedMqListener listener;

    @BeforeEach
    void setUp() {
        handler = new TicketCreatedEventHandler(ticketNotificationMapper, new ObjectMapper());
        listener = new TicketCreatedMqListener(handler);
    }

    @Test
    void consumeCreatesCustomerNotification() {
        when(ticketNotificationMapper.countByEventId("evt-1")).thenReturn(0);
        when(ticketNotificationMapper.insert(any(TicketNotification.class))).thenReturn(1);

        boolean created = handler.handle(PAYLOAD);

        assertTrue(created);
        ArgumentCaptor<TicketNotification> captor = ArgumentCaptor.forClass(TicketNotification.class);
        verify(ticketNotificationMapper).insert(captor.capture());
        assertEquals("evt-1", captor.getValue().getEventId());
        assertEquals(100L, captor.getValue().getTicketId());
        assertEquals(1L, captor.getValue().getCustomerUserId());
        assertEquals("工单已创建：TKT-ABC", captor.getValue().getContent());
    }

    @Test
    void duplicateEventDoesNotInsertTwice() {
        when(ticketNotificationMapper.countByEventId("evt-1")).thenReturn(0, 1);
        when(ticketNotificationMapper.insert(any(TicketNotification.class))).thenReturn(1);

        assertTrue(handler.handle(PAYLOAD));
        assertFalse(handler.handle(PAYLOAD));
        verify(ticketNotificationMapper, times(1)).insert(any(TicketNotification.class));
    }

    @Test
    void duplicateKeyIsTreatedAsIdempotentSuccess() {
        when(ticketNotificationMapper.countByEventId("evt-1")).thenReturn(0);
        when(ticketNotificationMapper.insert(any(TicketNotification.class)))
                .thenThrow(new DuplicateKeyException("uk_ticket_notification_event_id"));

        assertFalse(handler.handle(PAYLOAD));
    }

    @Test
    void listenerDropsInvalidJsonWithoutRetry() {
        MessageExt message = new MessageExt();
        message.setBody("not-json".getBytes(StandardCharsets.UTF_8));

        ConsumeConcurrentlyStatus status = listener.consumeMessage(List.of(message), null);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        verify(ticketNotificationMapper, never()).insert(any());
        verify(ticketNotificationMapper, never()).countByEventId(any());
    }

    @Test
    void listenerDropsPayloadMissingRequiredFields() {
        MessageExt message = new MessageExt();
        message.setBody("{\"eventId\":\"\",\"ticketId\":null}".getBytes(StandardCharsets.UTF_8));

        ConsumeConcurrentlyStatus status = listener.consumeMessage(List.of(message), null);

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, status);
        verify(ticketNotificationMapper, never()).insert(any());
    }

    @Test
    void listenerReconsumesWhenHandlerFails() {
        when(ticketNotificationMapper.countByEventId("evt-1")).thenThrow(new IllegalStateException("db down"));
        MessageExt message = new MessageExt();
        message.setBody(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        ConsumeConcurrentlyStatus status = listener.consumeMessage(List.of(message), null);

        assertEquals(ConsumeConcurrentlyStatus.RECONSUME_LATER, status);
        verify(ticketNotificationMapper, never()).insert(any());
    }

    @Test
    void listenerSucceedsWhenHandleCompletes() {
        when(ticketNotificationMapper.countByEventId("evt-1")).thenReturn(0);
        when(ticketNotificationMapper.insert(any(TicketNotification.class))).thenReturn(1);
        MessageExt message = new MessageExt();
        message.setBody(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        assertEquals(ConsumeConcurrentlyStatus.CONSUME_SUCCESS, listener.consumeMessage(List.of(message), null));
    }
}
