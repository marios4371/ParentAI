package com.example.chat_microservice.chat.chat_microservice.controller;

import com.example.chat_microservice.chat.chat_microservice.dto.ConversationDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.MessageDTO;
import com.example.chat_microservice.chat.chat_microservice.service.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private Logger logger = LoggerFactory.getLogger(ChatController.class);
    private ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> listConversations(@RequestHeader(value="X-User-Id", required=false) String userIdHeader) {
        logger.info("GET /api/conversations called. X-User-Id header = {}", userIdHeader);
        try {
            Long userId = Long.valueOf(userIdHeader);
            List<ConversationDTO> list = conversationService.listConversations(userId);
            logger.info("Returning {} conversations for userId={}", list.size(), userId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            logger.warn("listConversations failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error","invalid_or_missing_user_id"));
        }
    }

    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(@RequestHeader(value="X-User-Id", required=false) String userIdHeader,
                                                @RequestBody(required=false) ConversationDTO body) {
        try {
            Long userId = Long.valueOf(userIdHeader);
            ConversationDTO created = conversationService.createConversation(userId, body);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error","invalid_or_missing_user_id"));
        }
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<?> getMessages(@RequestHeader(value="X-User-Id", required=false) String userIdHeader,
                                         @PathVariable("id") Long convId) {
        try {
            Long userId = Long.valueOf(userIdHeader);
            return ResponseEntity.ok(conversationService.getMessages(userId, convId));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<?> sendMessage(@RequestHeader(value="X-User-Id", required=false) String userIdHeader,
                                         @PathVariable("id") Long convId,
                                         @RequestBody Map<String,String> body) {
        try {
            Long userId = Long.valueOf(userIdHeader);
            String message = body.get("message");
            MessageDTO botMsg = conversationService.sendMessage(userId, convId, message);
            return ResponseEntity.ok(Map.of("reply", botMsg.getText(), "botMessage", botMsg));
        } catch (NumberFormatException nfe) {
            return ResponseEntity.badRequest().body(Map.of("error","invalid_user_id"));
        } catch (IllegalArgumentException ia) {
            return ResponseEntity.status(403).body(Map.of("error", ia.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error","server_error","message", e.getMessage()));
        }
    }
}
