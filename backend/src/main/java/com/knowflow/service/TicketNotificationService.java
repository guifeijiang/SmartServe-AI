package com.knowflow.service;

import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketNotificationResponse;
import com.knowflow.entity.TicketNotification;
import com.knowflow.mapper.TicketNotificationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketNotificationService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final TicketNotificationMapper ticketNotificationMapper;

    public TicketNotificationService(TicketNotificationMapper ticketNotificationMapper) {
        this.ticketNotificationMapper = ticketNotificationMapper;
    }

    public PageResponse<TicketNotificationResponse> listMine(Long userId, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = (safePage - 1) * safeSize;
        long total = ticketNotificationMapper.countByCustomerUserId(userId);
        List<TicketNotificationResponse> items = ticketNotificationMapper
                .findPageByCustomerUserId(userId, offset, safeSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    private TicketNotificationResponse toResponse(TicketNotification notification) {
        TicketNotificationResponse response = new TicketNotificationResponse();
        response.setId(notification.getId());
        response.setEventId(notification.getEventId());
        response.setTicketId(notification.getTicketId());
        response.setCustomerUserId(notification.getCustomerUserId());
        response.setContent(notification.getContent());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
}
