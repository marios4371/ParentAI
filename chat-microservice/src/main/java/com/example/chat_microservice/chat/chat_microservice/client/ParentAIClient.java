package com.example.chat_microservice.chat.chat_microservice.client;

import com.example.chat_microservice.chat.chat_microservice.dto.client.HFChatRequest;
import com.example.chat_microservice.chat.chat_microservice.dto.client.HFChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class ParentAIClient {
    private static final Logger log = LoggerFactory.getLogger(ParentAIClient.class);

    private final WebClient webClient;

    @Value("${parentai.default-model:open-mistral-7b}")
    private String defaultModel;

    @Value("${parentai.timeout-ms:30000}")
    private int timeoutMs;

    public ParentAIClient(@Value("${parentai.base-url:http://localhost:8001}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("ParentAIClient baseUrl={}", baseUrl);
    }

    public HFChatResponse chat(String message, Integer maxNewTokens, Double temperature) {
        HFChatRequest req = new HFChatRequest(message, defaultModel, maxNewTokens, temperature);
        log.info("ParentAIClient -> POST /chat body={}", req);

        HFChatResponse resp = webClient.post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(HFChatResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();


//          ***DEBUG RESPONSE***
//        log.info("ParentAIClient <- {}", resp);


        if (resp == null) {
            throw new RuntimeException("parent_ai_unavailable");
        }
        return resp;
    }
}