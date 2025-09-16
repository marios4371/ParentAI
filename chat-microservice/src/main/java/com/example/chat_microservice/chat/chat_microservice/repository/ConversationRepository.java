package com.example.chat_microservice.chat.chat_microservice.repository;

import com.example.chat_microservice.chat.chat_microservice.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long>{
    List<Conversation> findByUserIdOrderByCreatedAtDesc(Long userId);

}
