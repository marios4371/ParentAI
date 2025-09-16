package com.example.chat_microservice.chat.chat_microservice.service;

import com.example.chat_microservice.chat.chat_microservice.dto.ConversationDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.MessageDTO;

import java.util.List;

public interface ConversationService {

    List<ConversationDTO> listConversations(Long userId);
    ConversationDTO createConversation(Long userId, ConversationDTO conversationDTO);

    List<MessageDTO> getMessages(Long userId, Long conversationId);

    MessageDTO sendMessage(Long userId, Long conversationId, String text);

    MessageDTO legacyChat(Long userId, String text);
}
