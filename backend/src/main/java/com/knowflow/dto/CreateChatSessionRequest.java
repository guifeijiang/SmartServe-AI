package com.knowflow.dto;

import jakarta.validation.constraints.Size;

public class CreateChatSessionRequest {

    @Size(max = 255, message = "标题不能超过 255 个字符")
    private String title;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
