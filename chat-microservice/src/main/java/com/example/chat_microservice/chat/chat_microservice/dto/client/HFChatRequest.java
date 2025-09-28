package com.example.chat_microservice.chat.chat_microservice.dto.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HFChatRequest {
    private String message;
    private String model;

    @JsonProperty("max_new_tokens")
    private Integer maxNewTokens;

    private Double temperature;

    public HFChatRequest() {}

    public HFChatRequest(String message, String model, Integer maxNewTokens, Double temperature) {
        this.message = message;
        this.model = model;
        this.maxNewTokens = maxNewTokens;
        this.temperature = temperature;
    }

    public String getMessage() { return message; }
    public String getModel() { return model; }
    public Integer getMaxNewTokens() { return maxNewTokens; }
    public Double getTemperature() { return temperature; }

    public void setMessage(String message) { this.message = message; }
    public void setModel(String model) { this.model = model; }
    public void setMaxNewTokens(Integer maxNewTokens) { this.maxNewTokens = maxNewTokens; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
}