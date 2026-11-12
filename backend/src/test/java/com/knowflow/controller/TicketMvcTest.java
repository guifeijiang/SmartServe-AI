package com.knowflow.controller;

import com.knowflow.common.ForbiddenException;
import com.knowflow.common.InvalidRequestException;
import com.knowflow.dto.AssignTicketRequest;
import com.knowflow.dto.CreateTicketRequest;
import com.knowflow.dto.TicketResponse;
import com.knowflow.service.JwtService;
import com.knowflow.service.TicketService;
import com.knowflow.ticket.TicketStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketMvcTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long OTHER_CUSTOMER_ID = 2L;
    private static final Long AGENT_ID = 10L;
    private static final Long TICKET_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void ticketApisRequireJwt() throws Exception {
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tickets/" + TICKET_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tickets/" + TICKET_ID + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentUserId\":10}"))
                .andExpect(status().isUnauthorized());

        verify(ticketService, never()).listMine(any(), any(), any());
        verify(ticketService, never()).create(any(), any());
        verify(ticketService, never()).get(any(), any());
        verify(ticketService, never()).assign(any(), any(), any());
    }

    @Test
    void createPassesJwtUserIdToService() throws Exception {
        when(ticketService.create(eq(CUSTOMER_ID), any(CreateTicketRequest.class))).thenReturn(ownedTicket());

        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + jwtService.createToken(CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TICKET_ID))
                .andExpect(jsonPath("$.customerUserId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.status").value(TicketStatus.NEW));

        ArgumentCaptor<CreateTicketRequest> captor = ArgumentCaptor.forClass(CreateTicketRequest.class);
        verify(ticketService).create(eq(CUSTOMER_ID), captor.capture());
        assertEquals("打印机无法出纸", captor.getValue().getTitle());
        verify(ticketService, never()).create(eq(OTHER_CUSTOMER_ID), any());
    }

    @Test
    void getOtherCustomerTicketReturnsForbidden() throws Exception {
        doThrow(new ForbiddenException("无权查看该工单"))
                .when(ticketService).get(OTHER_CUSTOMER_ID, TICKET_ID);

        mockMvc.perform(get("/api/tickets/" + TICKET_ID)
                        .header("Authorization", "Bearer " + jwtService.createToken(OTHER_CUSTOMER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(ticketService).get(OTHER_CUSTOMER_ID, TICKET_ID);
        verify(ticketService, never()).get(eq(CUSTOMER_ID), any());
    }

    @Test
    void nonAdminAssignReturnsForbidden() throws Exception {
        doThrow(new ForbiddenException("无权执行该操作"))
                .when(ticketService).assign(eq(CUSTOMER_ID), eq(TICKET_ID), any(AssignTicketRequest.class));

        mockMvc.perform(post("/api/tickets/" + TICKET_ID + "/assign")
                        .header("Authorization", "Bearer " + jwtService.createToken(CUSTOMER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentUserId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(ticketService).assign(eq(CUSTOMER_ID), eq(TICKET_ID), any(AssignTicketRequest.class));
    }

    @Test
    void illegalStatusTransitionReturnsInvalidRequest() throws Exception {
        doThrow(new InvalidRequestException("非法状态流转"))
                .when(ticketService).resolve(eq(AGENT_ID), eq(TICKET_ID), any());

        mockMvc.perform(post("/api/tickets/" + TICKET_ID + "/resolve")
                        .header("Authorization", "Bearer " + jwtService.createToken(AGENT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"提前关闭\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(ticketService).resolve(eq(AGENT_ID), eq(TICKET_ID), any());
    }

    @Test
    void getOwnTicketReturnsBody() throws Exception {
        when(ticketService.get(CUSTOMER_ID, TICKET_ID)).thenReturn(ownedTicket());

        mockMvc.perform(get("/api/tickets/" + TICKET_ID)
                        .header("Authorization", "Bearer " + jwtService.createToken(CUSTOMER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TICKET_ID))
                .andExpect(jsonPath("$.ticketNo").value("TKT-TEST"))
                .andExpect(jsonPath("$.title").value("打印机无法出纸"));
        verify(ticketService).get(CUSTOMER_ID, TICKET_ID);
    }

    private static String createBody() {
        return """
                {"title":"打印机无法出纸","description":"卡纸后无法继续打印","category":"HARDWARE","priority":"HIGH"}
                """;
    }

    private static TicketResponse ownedTicket() {
        TicketResponse response = new TicketResponse();
        response.setId(TICKET_ID);
        response.setTicketNo("TKT-TEST");
        response.setCustomerUserId(CUSTOMER_ID);
        response.setTitle("打印机无法出纸");
        response.setDescription("卡纸后无法继续打印");
        response.setCategory("HARDWARE");
        response.setPriority("HIGH");
        response.setStatus(TicketStatus.NEW);
        return response;
    }
}
