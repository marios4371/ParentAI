package com.example.chat_microservice.chat.chat_microservice.dto;

import com.example.chat_microservice.chat.chat_microservice.model.Conversation;

import javax.persistence.*;
import java.time.Instant;

public class MessageDTO {

    private Long id;

    private Long conversationId;

    private String sender;

    private String text;

    private Instant createdAt;

    public MessageDTO(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

