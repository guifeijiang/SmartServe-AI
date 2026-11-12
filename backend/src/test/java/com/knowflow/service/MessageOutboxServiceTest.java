package com.knowflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.RocketMqProperties;
import com.knowflow.entity.MessageOutbox;
import com.knowflow.entity.ServiceTicket;
import com.knowflow.mapper.MessageOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageOutboxServiceTest {

    @Mock
    private MessageOutboxMapper messageOutboxMapper;

    private MessageOutboxService messageOutboxService;

    @BeforeEach
    void setUp() {
        RocketMqProperties properties = new RocketMqProperties();
        properties.setTopic("TICKET_CREATED");
        properties.setTag("CREATED");
        messageOutboxService = new MessageOutboxService(messageOutboxMapper, properties, new ObjectMapper());
    }

    @Test
    void enqueueTicketCreatedWritesPendingOutbox() {
        when(messageOutboxMapper.insert(any(MessageOutbox.class))).thenReturn(1);
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(100L);
        ticket.setTicketNo("TKT-ABC");
        ticket.setCustomerUserId(1L);
        ticket.setTitle("打印机无法出纸");

        MessageOutbox saved = messageOutboxService.enqueueTicketCreated(ticket);

        ArgumentCaptor<MessageOutbox> captor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(messageOutboxMapper).insert(captor.capture());
        MessageOutbox outbox = captor.getValue();
        assertEquals(MessageOutbox.STATUS_PENDING, outbox.getStatus());
        assertEquals("TICKET_CREATED", outbox.getTopic());
        assertEquals("CREATED", outbox.getTag());
        assertEquals(0, outbox.getRetryCount());
        assertTrue(outbox.getPayload().contains("TKT-ABC"));
        assertTrue(outbox.getPayload().contains("\"ticketId\":100"));
        assertEquals(outbox.getEventId(), saved.getEventId());
    }
}
