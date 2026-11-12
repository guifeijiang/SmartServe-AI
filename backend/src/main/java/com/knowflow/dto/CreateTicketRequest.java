package com.knowflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTicketRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不能超过 128 个字符")
    private String title;

    @NotBlank(message = "问题描述不能为空")
    private String description;

    @NotBlank(message = "问题类型不能为空")
    @Size(max = 32, message = "问题类型不能超过 32 个字符")
    private String category;

    @NotBlank(message = "优先级不能为空")
    @Size(max = 32, message = "优先级不能超过 32 个字符")
    private String priority;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}
