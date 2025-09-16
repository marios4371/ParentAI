package com.example.chat_microservice.chat.chat_microservice.dto.eventDTO;

import java.time.Instant;

public class ChatCreatedEvent {
    private Long conversationId;
    private Long userId;
    private String title;
    private Instant createdAt;

    public ChatCreatedEvent(){}

    public ChatCreatedEvent(Long conversationId, Long userId, String title, Instant createdAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.title = title;
        this.createdAt = createdAt;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}