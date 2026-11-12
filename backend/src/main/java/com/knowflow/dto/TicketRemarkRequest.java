package com.knowflow.dto;

import jakarta.validation.constraints.Size;

public class TicketRemarkRequest {

    @Size(max = 512, message = "备注不能超过 512 个字符")
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
