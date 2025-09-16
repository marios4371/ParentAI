package com.example.chat_microservice.chat.chat_microservice.mapper;

import com.example.chat_microservice.chat.chat_microservice.dto.ConversationDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.MessageDTO;
import com.example.chat_microservice.chat.chat_microservice.model.Conversation;
import com.example.chat_microservice.chat.chat_microservice.model.Message;

import java.util.List;
import java.util.stream.Collectors;

public class ConversationMapper {
    public static ConversationDTO toDto(Conversation c) {
        if (c==null) return null;
        ConversationDTO dto = new ConversationDTO();
        dto.setId(c.getId());
        dto.setUserId(c.getUserId());
        dto.setTitle(c.getTitle());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setMessages(c.getMessages() == null ? List.of() :
                c.getMessages().stream().map(ConversationMapper::toMessageDto).collect(Collectors.toList()));
        return dto;
    }

    public static MessageDTO toMessageDto(Message m) {
        if (m==null) return null;
        MessageDTO md = new MessageDTO();
        md.setId(m.getId());
        md.setConversationId(m.getConversation() == null ? null : m.getConversation().getId());
        md.setSender(m.getSender());
        md.setText(m.getText());
        md.setCreatedAt(m.getCreatedAt());
        return md;
    }

    public static Conversation toEntity(ConversationDTO dto) {
        if(dto==null) return null;
        Conversation conv = new Conversation();
        conv.setUserId(dto.getUserId());
        conv.setTitle(dto.getTitle());
        conv.setCreatedAt(dto.getCreatedAt());
        return conv;
    }
    public static Message toMessageEntity(MessageDTO dto, Conversation conv) {
        if(dto==null || conv==null) return null;
        Message message = new Message();
        message.setConversation(conv);
        message.setSender(dto.getSender());
        message.setText(dto.getText());
        message.setCreatedAt(dto.getCreatedAt());
        return message;
    }
}
