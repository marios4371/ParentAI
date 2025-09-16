package com.example.chat_microservice.chat.chat_microservice.repository;

import com.example.chat_microservice.chat.chat_microservice.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAt(Long conversationId);
}
