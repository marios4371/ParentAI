package com.example.chat_microservice.chat.chat_microservice.service;


import com.example.chat_microservice.chat.chat_microservice.client.ParentAIClient;
import com.example.chat_microservice.chat.chat_microservice.dto.ConversationDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.MessageDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.client.HFChatResponse;
import com.example.chat_microservice.chat.chat_microservice.dto.eventDTO.ChatCreatedEvent;
import com.example.chat_microservice.chat.chat_microservice.mapper.ConversationMapper;
import com.example.chat_microservice.chat.chat_microservice.model.Conversation;
import com.example.chat_microservice.chat.chat_microservice.model.Message;
import com.example.chat_microservice.chat.chat_microservice.repository.ConversationRepository;
import com.example.chat_microservice.chat.chat_microservice.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClient;

import javax.transaction.Transactional;

@Service
public class ConversationServiceImpl implements ConversationService {


    private ConversationRepository conversationRepository;

    private MessageRepository messageRepository;
    private Logger logger = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private WebClient webClient;

    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, ChatCreatedEvent> kafkaTemplate;

    private final ParentAIClient parentAIClient;


    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   MessageRepository messageRepository,
                                   WebClient.Builder webClientBuilder,
                                   ObjectMapper objectMapper,
                                   ParentAIClient parentAIClient) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.parentAIClient = parentAIClient;
    }

    @Override
    public List<ConversationDTO> listConversations(Long userId) {
        if (userId == null) throw new IllegalArgumentException("userId cannot be null");
        logger.info("listConversations() called for userId={}", userId);
        List<Conversation> conversationList = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        logger.info("conversationRepository returned {} rows for userId={}", conversationList.size(), userId);
        return conversationList.stream()
                .map(ConversationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConversationDTO createConversation(Long userId, ConversationDTO conversationDTO) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        try {
            if (conversationDTO == null) conversationDTO = new ConversationDTO();

            conversationDTO.setUserId(userId);

            String title = conversationDTO.getTitle();
            if (title == null || title.trim().isEmpty()) conversationDTO.setTitle("Conversation");
            else conversationDTO.setTitle(title.trim());

            Conversation entity = ConversationMapper.toEntity(conversationDTO);

            entity.setId(null);
            if (entity.getCreatedAt() == null) entity.setCreatedAt(Instant.now());
            if (entity.getMessages() == null) entity.setMessages(new java.util.ArrayList<>());

            Conversation saved = conversationRepository.save(entity);

            try {
                ChatCreatedEvent ev = new ChatCreatedEvent(saved.getId(), saved.getUserId(), saved.getTitle(), saved.getCreatedAt());
                kafkaTemplate.send("chat.created", String.valueOf(saved.getId()), ev);
            } catch (Exception e) {
                logger.warn("Failed to publish chat.created event for convId={} : {}", saved.getId(), e.getMessage(), e);
            }

            return ConversationMapper.toDto(saved);

        } catch (DataAccessException dae) {
            logger.error("DB error while creating conversation for userId={} : {}", userId, dae.getMessage(), dae);
            throw new RuntimeException("Database error creating conversation", dae);

        } catch (Exception ex) {
            logger.error("Unexpected error while creating conversation for userId={}: {}", userId, ex.getMessage(), ex);
            throw new RuntimeException("Failed to create conversation", ex);
        }
    }

    @Override
    public List<MessageDTO> getMessages(Long userId, Long conversationId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId must exists");
        }
        try{

            Optional<Conversation> conversation = conversationRepository.findById(conversationId);
            if(conversation.isEmpty()) throw new IllegalArgumentException("conversation not found");
            Conversation currConversation = conversation.get();

            if (!userId.equals(currConversation.getUserId())) throw new IllegalArgumentException("forbidden");
            List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAt(conversationId);

            return messages.stream().map(ConversationMapper::toMessageDto).collect(Collectors.toList());

        }catch(DataAccessException dae){
            logger.error("DB error while creating message for conversationId={} : {}", conversationId, dae.getMessage(), dae);
            throw new RuntimeException("Database error send messages", dae);
        }
    }

    private Conversation ensureConversation(Long userId, Long convId, String titleIfNew) {
        if (convId != null) {
            Optional<Conversation> maybe = conversationRepository.findById(convId);
            if (maybe.isPresent()) {
                Conversation existing = maybe.get();
                if (existing.getMessages() == null) {
                    existing.setMessages(new java.util.ArrayList<>());
                }
                return existing;
            }
        }
        Conversation c = new Conversation(userId, titleIfNew != null ? titleIfNew : "Conversation");
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        // init messages list
        if (c.getMessages() == null) c.setMessages(new java.util.ArrayList<>());
        return conversationRepository.save(c);
    }

    @Override
    @Transactional
    public MessageDTO sendMessage(Long userId, Long conversationId, String text) {
        if (userId == null) throw new IllegalArgumentException("userId must be provided");
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("empty message");

        Conversation conv = ensureConversation(userId, conversationId, null);
        if (!userId.equals(conv.getUserId())) throw new IllegalArgumentException("forbidden");
        if (conv.getMessages() == null) conv.setMessages(new java.util.ArrayList<>());

        // 1) save user message
        Message userMsg = new Message("user", text, Instant.now());
        userMsg.setConversation(conv);

        Message savedUserMsg;
        try {
            savedUserMsg = messageRepository.save(userMsg);
            if (conv.getMessages().stream().noneMatch(m -> m.getId() != null && m.getId().equals(savedUserMsg.getId()))) {
                conv.getMessages().add(savedUserMsg);
                conversationRepository.save(conv);
            }
        } catch (DataAccessException dae) {
            logger.error("DB error saving user message for userId={}, convId={} : {}", userId, conv.getId(), dae.getMessage(), dae);
            throw new RuntimeException("Database error saving message", dae);
        }

        // 2) call Python LLM proxy via ParentAIClient
        HFChatResponse aiResp;
        try {
            aiResp = parentAIClient.chat(text, 200, 0.7);
        } catch (RuntimeException e) {
            try {
                savedUserMsg.setText(savedUserMsg.getText() + " [FAILED: upstream]");
                messageRepository.save(savedUserMsg);
            } catch (Exception ee) {
                logger.warn("Failed to mark user message as failed: {}", ee.getMessage(), ee);
            }
            logger.error("Upstream python call failed for userId={}, convId={}: {}", userId, conv.getId(), e.getMessage(), e);
            throw new RuntimeException("Upstream error: " + e.getMessage(), e);
        }

        // 3) extract reply/model/metadata
        String botText = (aiResp != null && aiResp.getReply() != null) ? aiResp.getReply() : "(no reply)";
        String model = (aiResp != null) ? aiResp.getModel() : null;
        String metadataJson = null;
        try {
            if (aiResp != null && aiResp.getRaw() != null) {
                metadataJson = objectMapper.writeValueAsString(aiResp.getRaw());
            }
        } catch (Exception ignore) {}

        // 4) save bot message including metadata
        Message botMsg = new Message();
        botMsg.setSender("bot");
        botMsg.setText(botText);
        botMsg.setCreatedAt(Instant.now());
        botMsg.setConversation(conv);
        botMsg.setSource("python-service");
        if (model != null) botMsg.setModel(model);
        if (metadataJson != null) botMsg.setMetadata(metadataJson);

        Message savedBotMsg;
        try {
            savedBotMsg = messageRepository.save(botMsg);
            if (conv.getMessages().stream().noneMatch(m -> m.getId() != null && m.getId().equals(savedBotMsg.getId()))) {
                conv.getMessages().add(savedBotMsg);
                conversationRepository.save(conv);
            }
            logger.info("Saved bot message id={} for convId={} (userId={})", savedBotMsg.getId(), conv.getId(), userId);
        } catch (DataAccessException dae) {
            logger.error("DB error saving bot message for userId={}, convId={} : {}", userId, conv.getId(), dae.getMessage(), dae);
            throw new RuntimeException("Database error saving bot reply", dae);
        }

        return ConversationMapper.toMessageDto(savedBotMsg);
    }


    @Override
    public MessageDTO legacyChat(Long userId, String text) {

        if(userId == null) throw new IllegalArgumentException("user Id cannot be null");
        if(text==null) throw new IllegalArgumentException("text must be provided");
        Conversation conversation;
        List<Conversation> conversationList = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if(conversationList.isEmpty()){
            conversation = ensureConversation(userId, null, "Conversation1");
        }
        else {
            conversation = conversationList.get(0);
        }
        return sendMessage(userId, conversation.getId(), text);
    }
}
