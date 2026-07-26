package com.example.backend.dto;

import javax.validation.constraints.NotBlank;

/**
 * AI 聊天请求 DTO：与现有 {@link AiDiagnosisRequest} 独立，
 * 使用 {@code message} 替代 {@code symptoms}（聊天无必填症状）。
 */
public class ChatRequest {

    /** 对话 ID，null 时自动创建新对话 */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    private String message;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
