package com.knowflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.config.RocketMqProperties;
import com.knowflow.dto.TicketCreatedEvent;
import com.knowflow.entity.MessageOutbox;
import com.knowflow.entity.ServiceTicket;
import com.knowflow.mapper.MessageOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MessageOutboxService {

    private final MessageOutboxMapper messageOutboxMapper;
    private final RocketMqProperties rocketMqProperties;
    private final ObjectMapper objectMapper;

    public MessageOutboxService(
            MessageOutboxMapper messageOutboxMapper,
            RocketMqProperties rocketMqProperties,
            ObjectMapper objectMapper
    ) {
        this.messageOutboxMapper = messageOutboxMapper;
        this.rocketMqProperties = rocketMqProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageOutbox enqueueTicketCreated(ServiceTicket ticket) {
        String eventId = UUID.randomUUID().toString();
        TicketCreatedEvent event = new TicketCreatedEvent(
                eventId,
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getCustomerUserId(),
                ticket.getTitle()
        );
        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(eventId);
        outbox.setTopic(rocketMqProperties.getTopic());
        outbox.setTag(rocketMqProperties.getTag());
        outbox.setPayload(writePayload(event));
        outbox.setStatus(MessageOutbox.STATUS_PENDING);
        outbox.setRetryCount(0);
        messageOutboxMapper.insert(outbox);
        return outbox;
    }

    private String writePayload(TicketCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("工单事件序列化失败", ex);
        }
    }
}
