package com.knowflow.controller;

import com.knowflow.dto.AssignTicketRequest;
import com.knowflow.dto.CreateTicketRequest;
import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketOperationResponse;
import com.knowflow.dto.TicketRemarkRequest;
import com.knowflow.dto.TicketResponse;
import com.knowflow.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(Authentication authentication, @Valid @RequestBody CreateTicketRequest request) {
        return ticketService.create(currentUserId(authentication), request);
    }

    @GetMapping
    public PageResponse<TicketResponse> list(
            Authentication authentication,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ticketService.listMine(currentUserId(authentication), page, size);
    }

    @GetMapping("/{id}")
    public TicketResponse get(Authentication authentication, @PathVariable Long id) {
        return ticketService.get(currentUserId(authentication), id);
    }

    @PostMapping("/{id}/assign")
    public TicketResponse assign(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request
    ) {
        return ticketService.assign(currentUserId(authentication), id, request);
    }

    @PostMapping("/{id}/claim")
    public TicketResponse claim(Authentication authentication, @PathVariable Long id) {
        return ticketService.claim(currentUserId(authentication), id);
    }

    @PostMapping("/{id}/handle")
    public TicketResponse handle(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) TicketRemarkRequest request
    ) {
        return ticketService.handle(currentUserId(authentication), id, request == null ? new TicketRemarkRequest() : request);
    }

    @PostMapping("/{id}/resolve")
    public TicketResponse resolve(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) TicketRemarkRequest request
    ) {
        return ticketService.resolve(currentUserId(authentication), id, request == null ? new TicketRemarkRequest() : request);
    }

    @PostMapping("/{id}/close")
    public TicketResponse close(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody(required = false) TicketRemarkRequest request
    ) {
        return ticketService.close(currentUserId(authentication), id, request == null ? new TicketRemarkRequest() : request);
    }

    @GetMapping("/{id}/operations")
    public List<TicketOperationResponse> operations(Authentication authentication, @PathVariable Long id) {
        return ticketService.listOperations(currentUserId(authentication), id);
    }

    private Long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
