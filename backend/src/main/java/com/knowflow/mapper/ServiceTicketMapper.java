package com.knowflow.mapper;

import com.knowflow.entity.ServiceTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ServiceTicketMapper {

    int insert(ServiceTicket ticket);

    ServiceTicket findActiveById(@Param("id") Long id);

    long countByFilter(
            @Param("customerUserId") Long customerUserId,
            @Param("assignedAgentId") Long assignedAgentId
    );

    List<ServiceTicket> findPageByFilter(
            @Param("customerUserId") Long customerUserId,
            @Param("assignedAgentId") Long assignedAgentId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    int assignIfNew(
            @Param("id") Long id,
            @Param("agentId") Long agentId
    );

    int claimIfAssignedToAgent(
            @Param("id") Long id,
            @Param("agentId") Long agentId
    );

    int touchIfInProgressByAgent(
            @Param("id") Long id,
            @Param("agentId") Long agentId
    );

    int resolveIfInProgressByAgent(
            @Param("id") Long id,
            @Param("agentId") Long agentId
    );

    int closeIfResolved(
            @Param("id") Long id,
            @Param("customerUserId") Long customerUserId
    );
}
