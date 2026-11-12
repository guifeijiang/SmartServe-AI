package com.knowflow.service;

import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketNotificationResponse;
import com.knowflow.entity.TicketNotification;
import com.knowflow.mapper.TicketNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketNotificationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Mock
    private TicketNotificationMapper ticketNotificationMapper;

    private TicketNotificationService ticketNotificationService;

    @BeforeEach
    void setUp() {
        ticketNotificationService = new TicketNotificationService(ticketNotificationMapper);
    }

    @Test
    void listMineQueriesOnlyCurrentUser() {
        TicketNotification mine = new TicketNotification();
        mine.setId(10L);
        mine.setEventId("evt-1");
        mine.setTicketId(100L);
        mine.setCustomerUserId(OWNER_ID);
        mine.setContent("工单已创建：TKT-ABC");
        when(ticketNotificationMapper.countByCustomerUserId(OWNER_ID)).thenReturn(1L);
        when(ticketNotificationMapper.findPageByCustomerUserId(OWNER_ID, 0, 20)).thenReturn(List.of(mine));

        PageResponse<TicketNotificationResponse> page = ticketNotificationService.listMine(OWNER_ID, 1, 20);

        assertEquals(1, page.getTotal());
        assertEquals(OWNER_ID, page.getItems().get(0).getCustomerUserId());
        assertEquals("工单已创建：TKT-ABC", page.getItems().get(0).getContent());
        verify(ticketNotificationMapper).countByCustomerUserId(OWNER_ID);
        verify(ticketNotificationMapper).findPageByCustomerUserId(eq(OWNER_ID), eq(0), eq(20));
        verify(ticketNotificationMapper, never()).countByCustomerUserId(OTHER_ID);
        verify(ticketNotificationMapper, never()).findPageByCustomerUserId(eq(OTHER_ID), anyInt(), anyInt());
    }
}
