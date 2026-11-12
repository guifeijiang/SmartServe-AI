package com.knowflow.controller;

import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketNotificationResponse;
import com.knowflow.service.JwtService;
import com.knowflow.service.TicketNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketNotificationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TicketNotificationService ticketNotificationService;

    @Test
    void listMineRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isUnauthorized());
        verify(ticketNotificationService, never()).listMine(any(), any(), any());
    }

    @Test
    void listMineUsesCurrentUserId() throws Exception {
        TicketNotificationResponse item = new TicketNotificationResponse();
        item.setId(10L);
        item.setCustomerUserId(1L);
        item.setContent("工单已创建：TKT-ABC");
        when(ticketNotificationService.listMine(1L, 1, 10))
                .thenReturn(new PageResponse<>(List.of(item), 1, 1, 10));

        mockMvc.perform(get("/api/notifications/me")
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + jwtService.createToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].customerUserId").value(1))
                .andExpect(jsonPath("$.items[0].content").value("工单已创建：TKT-ABC"));
        verify(ticketNotificationService).listMine(1L, 1, 10);
        verify(ticketNotificationService, never()).listMine(eq(2L), any(), any());
    }

    @Test
    void listMineDoesNotUseAnotherUsersTokenSubject() throws Exception {
        when(ticketNotificationService.listMine(2L, null, null))
                .thenReturn(new PageResponse<>(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/notifications/me")
                        .header("Authorization", "Bearer " + jwtService.createToken(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        verify(ticketNotificationService).listMine(2L, null, null);
        verify(ticketNotificationService, never()).listMine(eq(1L), any(), any());
    }
}
