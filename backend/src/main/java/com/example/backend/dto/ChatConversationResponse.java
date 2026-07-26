package com.example.backend.dto;

import java.time.LocalDateTime;

/**
 * 对话列表响应 DTO：用于 GET /api/ai/conversations 返回。
 */
public class ChatConversationResponse {

    private Long id;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 最后一条消息的摘要（截取前 80 字符） */
    private String lastMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }
}
