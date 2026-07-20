package com.hanwha.ai.chat.domain;

import java.time.LocalDateTime;

public class ChatMessage {
    private Long id;
    private Long conversationId;
    private String role;
    private String message;
    private String attachmentName;
    private byte[] attachmentContent;
    private LocalDateTime createdAt;

    public ChatMessage() {
    }

    public ChatMessage(Long id, String role, String message, LocalDateTime createdAt) {
        this(id, null, role, message, null, createdAt);
    }

    public ChatMessage(Long id, Long conversationId, String role, String message,
            String attachmentName, LocalDateTime createdAt) {
        this(id, conversationId, role, message, attachmentName, null, createdAt);
    }

    public ChatMessage(Long id, Long conversationId, String role, String message,
            String attachmentName, byte[] attachmentContent, LocalDateTime createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.message = message;
        this.attachmentName = attachmentName;
        this.attachmentContent = attachmentContent;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public byte[] getAttachmentContent() { return attachmentContent; }
    public void setAttachmentContent(byte[] attachmentContent) { this.attachmentContent = attachmentContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
