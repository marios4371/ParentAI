package com.example.chat_microservice.chat.chat_microservice.dto;

import com.example.chat_microservice.chat.chat_microservice.model.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ConversationDTO {
    private Long id;

    private Long userId;

    private String title;

    private Instant createdAt;

    private List<MessageDTO> messages;

    public ConversationDTO(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<MessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }
}
