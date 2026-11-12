package com.knowflow.service;

import com.knowflow.common.ForbiddenException;
import com.knowflow.common.InvalidRequestException;
import com.knowflow.dto.AssignTicketRequest;
import com.knowflow.dto.CreateTicketRequest;
import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketRemarkRequest;
import com.knowflow.dto.TicketResponse;
import com.knowflow.entity.ServiceTicket;
import com.knowflow.entity.SysUser;
import com.knowflow.entity.TicketOperationLog;
import com.knowflow.mapper.ServiceTicketMapper;
import com.knowflow.mapper.SysUserMapper;
import com.knowflow.mapper.TicketOperationLogMapper;
import com.knowflow.ticket.TicketOperationType;
import com.knowflow.ticket.TicketStatus;
import com.knowflow.ticket.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final Long CUSTOMER_ID = 1L;
    private static final Long OTHER_CUSTOMER_ID = 2L;
    private static final Long AGENT_ID = 10L;
    private static final Long OTHER_AGENT_ID = 11L;
    private static final Long ADMIN_ID = 99L;
    private static final Long TICKET_ID = 100L;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private ServiceTicketMapper serviceTicketMapper;

    @Mock
    private TicketOperationLogMapper ticketOperationLogMapper;

    @Mock
    private MessageOutboxService messageOutboxService;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                sysUserMapper, serviceTicketMapper, ticketOperationLogMapper, messageOutboxService
        );
    }

    @Test
    void createTicketSucceedsForCustomer() {
        when(sysUserMapper.findById(CUSTOMER_ID)).thenReturn(user(CUSTOMER_ID, UserRole.CUSTOMER));
        when(serviceTicketMapper.insert(any(ServiceTicket.class))).thenAnswer(invocation -> {
            ServiceTicket ticket = invocation.getArgument(0);
            ticket.setId(TICKET_ID);
            return 1;
        });
        when(serviceTicketMapper.findActiveById(TICKET_ID)).thenReturn(newTicket(TicketStatus.NEW, CUSTOMER_ID, null));

        TicketResponse response = ticketService.create(CUSTOMER_ID, createRequest());

        ArgumentCaptor<ServiceTicket> ticketCaptor = ArgumentCaptor.forClass(ServiceTicket.class);
        verify(serviceTicketMapper).insert(ticketCaptor.capture());
        ServiceTicket saved = ticketCaptor.getValue();
        assertEquals(CUSTOMER_ID, saved.getCustomerUserId());
        assertEquals(TicketStatus.NEW, saved.getStatus());
        assertNotNull(saved.getTicketNo());
        assertEquals("打印机无法出纸", saved.getTitle());

        ArgumentCaptor<TicketOperationLog> logCaptor = ArgumentCaptor.forClass(TicketOperationLog.class);
        verify(ticketOperationLogMapper).insert(logCaptor.capture());
        TicketOperationLog log = logCaptor.getValue();
        assertEquals(TICKET_ID, log.getTicketId());
        assertEquals(CUSTOMER_ID, log.getOperatorUserId());
        assertEquals(TicketOperationType.CREATE, log.getOperationType());
        assertEquals(TicketStatus.NEW, log.getToStatus());

        InOrder order = inOrder(serviceTicketMapper, ticketOperationLogMapper, messageOutboxService);
        order.verify(serviceTicketMapper).insert(any(ServiceTicket.class));
        order.verify(ticketOperationLogMapper).insert(any(TicketOperationLog.class));
        order.verify(messageOutboxService).enqueueTicketCreated(any(ServiceTicket.class));

        assertEquals(TICKET_ID, response.getId());
        assertEquals(TicketStatus.NEW, response.getStatus());
    }

    @Test
    void customerCannotViewOtherCustomerTicket() {
        when(sysUserMapper.findById(OTHER_CUSTOMER_ID)).thenReturn(user(OTHER_CUSTOMER_ID, UserRole.CUSTOMER));
        when(serviceTicketMapper.findActiveById(TICKET_ID)).thenReturn(newTicket(TicketStatus.NEW, CUSTOMER_ID, null));

        assertThrows(ForbiddenException.class, () -> ticketService.get(OTHER_CUSTOMER_ID, TICKET_ID));
    }

    @Test
    void customerListUsesOwnUserIdForDatabasePaging() {
        when(sysUserMapper.findById(CUSTOMER_ID)).thenReturn(user(CUSTOMER_ID, UserRole.CUSTOMER));
        when(serviceTicketMapper.countByFilter(CUSTOMER_ID, null)).thenReturn(1L);
        when(serviceTicketMapper.findPageByFilter(CUSTOMER_ID, null, 0, 20))
                .thenReturn(List.of(newTicket(TicketStatus.NEW, CUSTOMER_ID, null)));

        PageResponse<TicketResponse> page = ticketService.listMine(CUSTOMER_ID, 1, 20);

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getItems().size());
        verify(serviceTicketMapper).countByFilter(CUSTOMER_ID, null);
        verify(serviceTicketMapper).findPageByFilter(eq(CUSTOMER_ID), isNull(), eq(0), eq(20));
    }

    @Test
    void agentCannotHandleTicketAssignedToSomeoneElse() {
        when(sysUserMapper.findById(OTHER_AGENT_ID)).thenReturn(user(OTHER_AGENT_ID, UserRole.AGENT));
        when(serviceTicketMapper.findActiveById(TICKET_ID))
                .thenReturn(newTicket(TicketStatus.IN_PROGRESS, CUSTOMER_ID, AGENT_ID));

        assertThrows(ForbiddenException.class, () -> ticketService.handle(OTHER_AGENT_ID, TICKET_ID, remark("跟进")));
        verify(serviceTicketMapper, never()).touchIfInProgressByAgent(any(), any());
    }

    @Test
    void adminCanAssignNewTicketToAgent() {
        when(sysUserMapper.findById(ADMIN_ID)).thenReturn(user(ADMIN_ID, UserRole.ADMIN));
        when(sysUserMapper.findById(AGENT_ID)).thenReturn(user(AGENT_ID, UserRole.AGENT));
        when(serviceTicketMapper.findActiveById(TICKET_ID))
                .thenReturn(newTicket(TicketStatus.NEW, CUSTOMER_ID, null))
                .thenReturn(assignedTicket());
        when(serviceTicketMapper.assignIfNew(TICKET_ID, AGENT_ID)).thenReturn(1);

        AssignTicketRequest request = new AssignTicketRequest();
        request.setAgentUserId(AGENT_ID);
        TicketResponse response = ticketService.assign(ADMIN_ID, TICKET_ID, request);

        verify(serviceTicketMapper).assignIfNew(TICKET_ID, AGENT_ID);
        ArgumentCaptor<TicketOperationLog> logCaptor = ArgumentCaptor.forClass(TicketOperationLog.class);
        verify(ticketOperationLogMapper).insert(logCaptor.capture());
        assertEquals(TicketOperationType.ASSIGN, logCaptor.getValue().getOperationType());
        assertEquals(TicketStatus.NEW, logCaptor.getValue().getFromStatus());
        assertEquals(TicketStatus.ASSIGNED, logCaptor.getValue().getToStatus());
        assertEquals(TicketStatus.ASSIGNED, response.getStatus());
        assertEquals(AGENT_ID, response.getAssignedAgentId());
    }

    @Test
    void illegalStatusTransitionIsRejected() {
        when(sysUserMapper.findById(AGENT_ID)).thenReturn(user(AGENT_ID, UserRole.AGENT));
        when(serviceTicketMapper.findActiveById(TICKET_ID))
                .thenReturn(newTicket(TicketStatus.NEW, CUSTOMER_ID, AGENT_ID));

        assertThrows(InvalidRequestException.class, () -> ticketService.resolve(AGENT_ID, TICKET_ID, remark("提前关闭")));
        verify(serviceTicketMapper, never()).resolveIfInProgressByAgent(any(), any());
        verify(ticketOperationLogMapper, never()).insert(any());
    }

    @Test
    void duplicateClaimIsRejected() {
        when(sysUserMapper.findById(AGENT_ID)).thenReturn(user(AGENT_ID, UserRole.AGENT));
        when(serviceTicketMapper.findActiveById(TICKET_ID)).thenReturn(assignedTicket());
        when(serviceTicketMapper.claimIfAssignedToAgent(TICKET_ID, AGENT_ID)).thenReturn(0);

        assertThrows(InvalidRequestException.class, () -> ticketService.claim(AGENT_ID, TICKET_ID));
        verify(ticketOperationLogMapper, never()).insert(any());
    }

    @Test
    void resolveWritesStatusAndMatchingOperationLog() {
        when(sysUserMapper.findById(AGENT_ID)).thenReturn(user(AGENT_ID, UserRole.AGENT));
        when(serviceTicketMapper.findActiveById(TICKET_ID))
                .thenReturn(newTicket(TicketStatus.IN_PROGRESS, CUSTOMER_ID, AGENT_ID))
                .thenReturn(resolvedTicket());
        when(serviceTicketMapper.resolveIfInProgressByAgent(TICKET_ID, AGENT_ID)).thenReturn(1);

        TicketResponse response = ticketService.resolve(AGENT_ID, TICKET_ID, remark("已更换配件"));

        InOrder order = inOrder(serviceTicketMapper, ticketOperationLogMapper);
        order.verify(serviceTicketMapper).resolveIfInProgressByAgent(TICKET_ID, AGENT_ID);
        ArgumentCaptor<TicketOperationLog> logCaptor = ArgumentCaptor.forClass(TicketOperationLog.class);
        order.verify(ticketOperationLogMapper).insert(logCaptor.capture());
        TicketOperationLog log = logCaptor.getValue();
        assertEquals(TicketOperationType.RESOLVE, log.getOperationType());
        assertEquals(TicketStatus.IN_PROGRESS, log.getFromStatus());
        assertEquals(TicketStatus.RESOLVED, log.getToStatus());
        assertEquals("已更换配件", log.getRemark());
        assertEquals(TicketStatus.RESOLVED, response.getStatus());
    }

    @Test
    void agentListUsesAssignedAgentIdPaging() {
        when(sysUserMapper.findById(AGENT_ID)).thenReturn(user(AGENT_ID, UserRole.AGENT));
        when(serviceTicketMapper.countByFilter(null, AGENT_ID)).thenReturn(1L);
        when(serviceTicketMapper.findPageByFilter(null, AGENT_ID, 20, 20))
                .thenReturn(List.of(assignedTicket()));

        PageResponse<TicketResponse> page = ticketService.listMine(AGENT_ID, 2, 20);

        assertEquals(1, page.getItems().size());
        verify(serviceTicketMapper).findPageByFilter(isNull(), eq(AGENT_ID), eq(20), eq(20));
    }

    private static CreateTicketRequest createRequest() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("打印机无法出纸");
        request.setDescription("卡纸后无法继续打印");
        request.setCategory("HARDWARE");
        request.setPriority("HIGH");
        return request;
    }

    private static TicketRemarkRequest remark(String text) {
        TicketRemarkRequest request = new TicketRemarkRequest();
        request.setRemark(text);
        return request;
    }

    private static SysUser user(Long id, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("u" + id);
        user.setStatus("ACTIVE");
        user.setRole(role);
        return user;
    }

    private static ServiceTicket newTicket(String status, Long customerId, Long agentId) {
        ServiceTicket ticket = new ServiceTicket();
        ticket.setId(TICKET_ID);
        ticket.setTicketNo("TKT-TEST");
        ticket.setCustomerUserId(customerId);
        ticket.setAssignedAgentId(agentId);
        ticket.setTitle("打印机无法出纸");
        ticket.setDescription("卡纸后无法继续打印");
        ticket.setCategory("HARDWARE");
        ticket.setPriority("HIGH");
        ticket.setStatus(status);
        return ticket;
    }

    private static ServiceTicket assignedTicket() {
        return newTicket(TicketStatus.ASSIGNED, CUSTOMER_ID, AGENT_ID);
    }

    private static ServiceTicket resolvedTicket() {
        return newTicket(TicketStatus.RESOLVED, CUSTOMER_ID, AGENT_ID);
    }
}
