package com.knowflow.service;

import com.knowflow.common.ForbiddenException;
import com.knowflow.common.InvalidRequestException;
import com.knowflow.common.ResourceNotFoundException;
import com.knowflow.dto.AssignTicketRequest;
import com.knowflow.dto.CreateTicketRequest;
import com.knowflow.dto.PageResponse;
import com.knowflow.dto.TicketOperationResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final SysUserMapper sysUserMapper;
    private final ServiceTicketMapper serviceTicketMapper;
    private final TicketOperationLogMapper ticketOperationLogMapper;
    private final MessageOutboxService messageOutboxService;

    public TicketService(
            SysUserMapper sysUserMapper,
            ServiceTicketMapper serviceTicketMapper,
            TicketOperationLogMapper ticketOperationLogMapper,
            MessageOutboxService messageOutboxService
    ) {
        this.sysUserMapper = sysUserMapper;
        this.serviceTicketMapper = serviceTicketMapper;
        this.ticketOperationLogMapper = ticketOperationLogMapper;
        this.messageOutboxService = messageOutboxService;
    }

    @Transactional
    public TicketResponse create(Long userId, CreateTicketRequest request) {
        requireRole(userId, UserRole.CUSTOMER);
        ServiceTicket ticket = new ServiceTicket();
        ticket.setTicketNo(newTicketNo());
        ticket.setCustomerUserId(userId);
        ticket.setTitle(request.getTitle().trim());
        ticket.setDescription(request.getDescription().trim());
        ticket.setCategory(request.getCategory().trim());
        ticket.setPriority(request.getPriority().trim());
        ticket.setStatus(TicketStatus.NEW);
        serviceTicketMapper.insert(ticket);
        insertLog(ticket.getId(), userId, TicketOperationType.CREATE, null, TicketStatus.NEW, "创建工单");
        messageOutboxService.enqueueTicketCreated(ticket);
        return toResponse(requireTicket(ticket.getId()));
    }

    public PageResponse<TicketResponse> listMine(Long userId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        int offset = (safePage - 1) * safeSize;
        String role = requireUser(userId).getRole();
        Long customerFilter = null;
        Long agentFilter = null;
        if (UserRole.CUSTOMER.equals(role)) {
            customerFilter = userId;
        } else if (UserRole.AGENT.equals(role)) {
            agentFilter = userId;
        } else if (!UserRole.ADMIN.equals(role)) {
            throw new ForbiddenException("无权查看工单");
        }
        long total = serviceTicketMapper.countByFilter(customerFilter, agentFilter);
        List<TicketResponse> items = serviceTicketMapper
                .findPageByFilter(customerFilter, agentFilter, offset, safeSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(items, total, safePage, safeSize);
    }

    public TicketResponse get(Long userId, Long ticketId) {
        return toResponse(requireVisibleTicket(userId, ticketId));
    }

    @Transactional
    public TicketResponse assign(Long userId, Long ticketId, AssignTicketRequest request) {
        requireRole(userId, UserRole.ADMIN);
        ServiceTicket ticket = requireTicket(ticketId);
        requireStatus(ticket, TicketStatus.NEW);
        SysUser agent = requireUser(request.getAgentUserId());
        if (!UserRole.AGENT.equals(normalizeRole(agent.getRole()))) {
            throw new InvalidRequestException("只能将工单分配给客服");
        }
        int updated = serviceTicketMapper.assignIfNew(ticketId, agent.getId());
        if (updated != 1) {
            throw new InvalidRequestException("工单状态已变化，无法分配");
        }
        insertLog(ticketId, userId, TicketOperationType.ASSIGN, TicketStatus.NEW, TicketStatus.ASSIGNED,
                "分配给客服 " + agent.getId());
        return toResponse(requireTicket(ticketId));
    }

    @Transactional
    public TicketResponse claim(Long userId, Long ticketId) {
        requireRole(userId, UserRole.AGENT);
        ServiceTicket ticket = requireTicket(ticketId);
        if (ticket.getAssignedAgentId() == null || !userId.equals(ticket.getAssignedAgentId())) {
            throw new ForbiddenException("无权处理该工单");
        }
        requireStatus(ticket, TicketStatus.ASSIGNED);
        int updated = serviceTicketMapper.claimIfAssignedToAgent(ticketId, userId);
        if (updated != 1) {
            throw new InvalidRequestException("工单已被接单或状态已变化");
        }
        insertLog(ticketId, userId, TicketOperationType.CLAIM, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, "客服接单");
        return toResponse(requireTicket(ticketId));
    }

    @Transactional
    public TicketResponse handle(Long userId, Long ticketId, TicketRemarkRequest request) {
        requireRole(userId, UserRole.AGENT);
        ServiceTicket ticket = requireAssignedAgentTicket(userId, ticketId);
        requireStatus(ticket, TicketStatus.IN_PROGRESS);
        int updated = serviceTicketMapper.touchIfInProgressByAgent(ticketId, userId);
        if (updated != 1) {
            throw new InvalidRequestException("工单状态已变化，无法处理");
        }
        insertLog(ticketId, userId, TicketOperationType.HANDLE, TicketStatus.IN_PROGRESS, TicketStatus.IN_PROGRESS,
                remarkOrDefault(request, "处理工单"));
        return toResponse(requireTicket(ticketId));
    }

    @Transactional
    public TicketResponse resolve(Long userId, Long ticketId, TicketRemarkRequest request) {
        requireRole(userId, UserRole.AGENT);
        ServiceTicket ticket = requireAssignedAgentTicket(userId, ticketId);
        requireStatus(ticket, TicketStatus.IN_PROGRESS);
        int updated = serviceTicketMapper.resolveIfInProgressByAgent(ticketId, userId);
        if (updated != 1) {
            throw new InvalidRequestException("工单状态已变化，无法解决");
        }
        insertLog(ticketId, userId, TicketOperationType.RESOLVE, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED,
                remarkOrDefault(request, "解决工单"));
        return toResponse(requireTicket(ticketId));
    }

    @Transactional
    public TicketResponse close(Long userId, Long ticketId, TicketRemarkRequest request) {
        SysUser operator = requireUser(userId);
        String role = normalizeRole(operator.getRole());
        ServiceTicket ticket = requireTicket(ticketId);
        if (UserRole.CUSTOMER.equals(role)) {
            if (!userId.equals(ticket.getCustomerUserId())) {
                throw new ForbiddenException("无权关闭该工单");
            }
        } else if (!UserRole.ADMIN.equals(role)) {
            throw new ForbiddenException("无权关闭工单");
        }
        requireStatus(ticket, TicketStatus.RESOLVED);
        Long customerFilter = UserRole.CUSTOMER.equals(role) ? userId : null;
        int updated = serviceTicketMapper.closeIfResolved(ticketId, customerFilter);
        if (updated != 1) {
            throw new InvalidRequestException("工单状态已变化，无法关闭");
        }
        insertLog(ticketId, userId, TicketOperationType.CLOSE, TicketStatus.RESOLVED, TicketStatus.CLOSED,
                remarkOrDefault(request, "关闭工单"));
        return toResponse(requireTicket(ticketId));
    }

    public List<TicketOperationResponse> listOperations(Long userId, Long ticketId) {
        requireVisibleTicket(userId, ticketId);
        return ticketOperationLogMapper.findByTicketId(ticketId).stream()
                .map(this::toOperationResponse)
                .toList();
    }

    private ServiceTicket requireVisibleTicket(Long userId, Long ticketId) {
        ServiceTicket ticket = requireTicket(ticketId);
        String role = requireUser(userId).getRole();
        if (UserRole.ADMIN.equals(normalizeRole(role))) {
            return ticket;
        }
        if (UserRole.AGENT.equals(normalizeRole(role))) {
            if (userId.equals(ticket.getAssignedAgentId())) {
                return ticket;
            }
            throw new ForbiddenException("无权查看该工单");
        }
        if (userId.equals(ticket.getCustomerUserId())) {
            return ticket;
        }
        throw new ForbiddenException("无权查看该工单");
    }

    private ServiceTicket requireAssignedAgentTicket(Long agentId, Long ticketId) {
        ServiceTicket ticket = requireTicket(ticketId);
        if (ticket.getAssignedAgentId() == null || !agentId.equals(ticket.getAssignedAgentId())) {
            throw new ForbiddenException("无权处理该工单");
        }
        return ticket;
    }

    private ServiceTicket requireTicket(Long ticketId) {
        ServiceTicket ticket = serviceTicketMapper.findActiveById(ticketId);
        if (ticket == null) {
            throw new ResourceNotFoundException("工单不存在");
        }
        return ticket;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.findById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new ForbiddenException("用户不存在或已停用");
        }
        return user;
    }

    private SysUser requireRole(Long userId, String expectedRole) {
        SysUser user = requireUser(userId);
        if (!expectedRole.equals(normalizeRole(user.getRole()))) {
            throw new ForbiddenException("无权执行该操作");
        }
        return user;
    }

    private void requireStatus(ServiceTicket ticket, String expected) {
        if (!expected.equals(ticket.getStatus())) {
            throw new InvalidRequestException("非法状态流转");
        }
    }

    private void insertLog(
            Long ticketId,
            Long operatorUserId,
            String operationType,
            String fromStatus,
            String toStatus,
            String remark
    ) {
        TicketOperationLog log = new TicketOperationLog();
        log.setTicketId(ticketId);
        log.setOperatorUserId(operatorUserId);
        log.setOperationType(operationType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setRemark(remark);
        ticketOperationLogMapper.insert(log);
    }

    private TicketResponse toResponse(ServiceTicket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setTicketNo(ticket.getTicketNo());
        response.setCustomerUserId(ticket.getCustomerUserId());
        response.setAssignedAgentId(ticket.getAssignedAgentId());
        response.setTitle(ticket.getTitle());
        response.setDescription(ticket.getDescription());
        response.setCategory(ticket.getCategory());
        response.setPriority(ticket.getPriority());
        response.setStatus(ticket.getStatus());
        response.setCreatedAt(ticket.getCreatedAt());
        response.setUpdatedAt(ticket.getUpdatedAt());
        response.setResolvedAt(ticket.getResolvedAt());
        return response;
    }

    private TicketOperationResponse toOperationResponse(TicketOperationLog log) {
        TicketOperationResponse response = new TicketOperationResponse();
        response.setId(log.getId());
        response.setTicketId(log.getTicketId());
        response.setOperatorUserId(log.getOperatorUserId());
        response.setOperationType(log.getOperationType());
        response.setFromStatus(log.getFromStatus());
        response.setToStatus(log.getToStatus());
        response.setRemark(log.getRemark());
        response.setCreatedAt(log.getCreatedAt());
        return response;
    }

    private static String remarkOrDefault(TicketRemarkRequest request, String fallback) {
        if (request == null || request.getRemark() == null || request.getRemark().isBlank()) {
            return fallback;
        }
        return request.getRemark().trim();
    }

    private static String newTicketNo() {
        return "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private static String normalizeRole(String role) {
        return role == null || role.isBlank() ? UserRole.CUSTOMER : role;
    }

    private static int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
