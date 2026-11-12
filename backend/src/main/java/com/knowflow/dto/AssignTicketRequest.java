package com.knowflow.dto;

import jakarta.validation.constraints.NotNull;

public class AssignTicketRequest {

    @NotNull(message = "客服 ID 不能为空")
    private Long agentUserId;

    public Long getAgentUserId() {
        return agentUserId;
    }

    public void setAgentUserId(Long agentUserId) {
        this.agentUserId = agentUserId;
    }
}
