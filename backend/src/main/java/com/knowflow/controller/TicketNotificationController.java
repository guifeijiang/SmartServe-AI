package com.knowflow.controller;

import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketNotificationResponse;
import com.knowflow.service.TicketNotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class TicketNotificationController {

    private final TicketNotificationService ticketNotificationService;

    public TicketNotificationController(TicketNotificationService ticketNotificationService) {
        this.ticketNotificationService = ticketNotificationService;
    }

    @GetMapping("/me")
    public PageResponse<TicketNotificationResponse> listMine(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ticketNotificationService.listMine((Long) authentication.getPrincipal(), page, size);
    }
}
