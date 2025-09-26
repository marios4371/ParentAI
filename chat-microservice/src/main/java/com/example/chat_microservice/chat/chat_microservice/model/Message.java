package com.example.chat_microservice.chat.chat_microservice.model;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="conversation_id")
    private Conversation conversation;

    @Column(name = "sender")
    private String sender;

    @Lob
    @Column(name = "text", nullable = false)
    private String text;

    @Column(name = "createdAt")
    private Instant createdAt;

    // --- New fields to persist LLM metadata ---
    @Column(name = "source")
    private String source; // e.g. "python-service"

    @Column(name = "model")
    private String model;  // e.g. "gpt-4" or "hf-llm-v1"

    @Lob
    @Column(name = "metadata")
    private String metadata; // raw JSON reply or serialized metadata

    public Message(){}

    // existing convenience constructor
    public Message(String sender, String text, Instant createdAt) {
        this.sender = sender;
        this.text = text;
        this.createdAt = createdAt;
    }

    // optional convenience constructor including metadata
    public Message(String sender, String text, Instant createdAt, String source, String model, String metadata) {
        this.sender = sender;
        this.text = text;
        this.createdAt = createdAt;
        this.source = source;
        this.model = model;
        this.metadata = metadata;
    }

    // --- getters / setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Conversation getConversation() { return conversation; }
    public void setConversation(Conversation conversation) { this.conversation = conversation; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
