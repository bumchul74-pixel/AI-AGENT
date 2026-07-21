package com.hanwha.ai.chat.domain;

import java.time.LocalDateTime;

public class ChatProject {
    private Long id;
    private String name;
    private int conversationCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getConversationCount() { return conversationCount; }
    public void setConversationCount(int conversationCount) { this.conversationCount = conversationCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
