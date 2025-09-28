package com.example.chat_microservice.chat.chat_microservice.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HFChatResponse {
    private String reply;
    private String model;
    private Map<String, Object> raw;

    public HFChatResponse() {}

    public String getReply() { return reply; }
    public String getModel() { return model; }
    public Map<String, Object> getRaw() { return raw; }

    public void setReply(String reply) { this.reply = reply; }
    public void setModel(String model) { this.model = model; }
    public void setRaw(Map<String, Object> raw) { this.raw = raw; }
}