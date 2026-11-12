package com.knowflow.dto;

public class TicketCreatedEvent {

    private String eventId;
    private Long ticketId;
    private String ticketNo;
    private Long customerUserId;
    private String title;

    public TicketCreatedEvent() {
    }

    public TicketCreatedEvent(String eventId, Long ticketId, String ticketNo, Long customerUserId, String title) {
        this.eventId = eventId;
        this.ticketId = ticketId;
        this.ticketNo = ticketNo;
        this.customerUserId = customerUserId;
        this.title = title;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public Long getCustomerUserId() {
        return customerUserId;
    }

    public void setCustomerUserId(Long customerUserId) {
        this.customerUserId = customerUserId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
