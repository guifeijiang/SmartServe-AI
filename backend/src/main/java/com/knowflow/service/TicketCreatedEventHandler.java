package com.knowflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.common.UnrecoverableMessageException;
import com.knowflow.dto.TicketCreatedEvent;
import com.knowflow.entity.TicketNotification;
import com.knowflow.mapper.TicketNotificationMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TicketCreatedEventHandler {

    private final TicketNotificationMapper ticketNotificationMapper;
    private final ObjectMapper objectMapper;

    public TicketCreatedEventHandler(TicketNotificationMapper ticketNotificationMapper, ObjectMapper objectMapper) {
        this.ticketNotificationMapper = ticketNotificationMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean handle(String payload) {
        TicketCreatedEvent event = parse(payload);
        if (ticketNotificationMapper.countByEventId(event.getEventId()) > 0) {
            return false;
        }
        TicketNotification notification = new TicketNotification();
        notification.setEventId(event.getEventId());
        notification.setTicketId(event.getTicketId());
        notification.setCustomerUserId(event.getCustomerUserId());
        notification.setContent("工单已创建：" + event.getTicketNo());
        try {
            ticketNotificationMapper.insert(notification);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicate(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private TicketCreatedEvent parse(String payload) {
        try {
            TicketCreatedEvent event = objectMapper.readValue(payload, TicketCreatedEvent.class);
            if (event == null || !StringUtils.hasText(event.getEventId())
                    || event.getTicketId() == null || event.getCustomerUserId() == null
                    || !StringUtils.hasText(event.getTicketNo())) {
                throw new UnrecoverableMessageException("工单创建事件缺少必要字段");
            }
            return event;
        } catch (UnrecoverableMessageException ex) {
            throw ex;
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new UnrecoverableMessageException("工单创建事件无法解析", ex);
        }
    }

    private static boolean isDuplicate(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("duplicate") || lower.contains("unique");
    }
}
