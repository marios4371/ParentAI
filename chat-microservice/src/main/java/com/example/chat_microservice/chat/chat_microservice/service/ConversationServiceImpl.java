package com.example.chat_microservice.chat.chat_microservice.service;


import com.example.chat_microservice.chat.chat_microservice.dto.ConversationDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.MessageDTO;
import com.example.chat_microservice.chat.chat_microservice.dto.eventDTO.ChatCreatedEvent;
import com.example.chat_microservice.chat.chat_microservice.mapper.ConversationMapper;
import com.example.chat_microservice.chat.chat_microservice.model.Conversation;
import com.example.chat_microservice.chat.chat_microservice.model.Message;
import com.example.chat_microservice.chat.chat_microservice.repository.ConversationRepository;
import com.example.chat_microservice.chat.chat_microservice.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   MessageRepository messageRepository,
                                   WebClient.Builder webClientBuilder,
                                   ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
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

            //DTO -> Entity
            Conversation entity = ConversationMapper.toEntity(conversationDTO);

            // Ensure new entity (ignore id from client)
            entity.setId(null);
            if (entity.getCreatedAt() == null) entity.setCreatedAt(Instant.now());

            if (entity.getMessages() != null) {
                for (Message m : entity.getMessages()) {
                    m.setConversation(entity);
                    if (m.getCreatedAt() == null) m.setCreatedAt(Instant.now());
                }
            }

            Conversation saved = conversationRepository.save(entity);

            try {
                ChatCreatedEvent ev = new ChatCreatedEvent(saved.getId(), saved.getUserId(), saved.getTitle(), saved.getCreatedAt());
                kafkaTemplate.send("chat.created", String.valueOf(saved.getId()), ev);
            } catch (Exception e) {
                logger.warn("Failed to publish chat.created event for convId={} : {}", saved.getId(), e.getMessage(), e);
            }

            //entity -> DTO
            ConversationDTO out = ConversationMapper.toDto(saved);
            return out;

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
            if (maybe.isPresent()) return maybe.get();
        }
        Conversation c = new Conversation(userId, titleIfNew != null ? titleIfNew : "Conversation");
        if (c.getCreatedAt() == null) c.setCreatedAt(Instant.now());
        return conversationRepository.save(c);
    }

    private String callPython(String message) {
        String pythonUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:8000/chat");
        try {
            String raw = webClient.post()
                    .uri(pythonUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(Map.of("message", message))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null) return "empty upstream response";

            try {
                JsonNode node = objectMapper.readTree(raw);
                if (node.has("reply")) return node.get("reply").asText();
                if (node.has("hf_response") && node.get("hf_response").has("generated_text"))
                    return node.get("hf_response").get("generated_text").asText();
            } catch (Exception ex) {
                // fallback to raw
            }
            return raw;
        } catch (Exception e) {
            logger.error("Error calling python service at {} : {}", pythonUrl, e.getMessage(), e);
            throw new RuntimeException("python call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public MessageDTO sendMessage(Long userId, Long conversationId, String text) {
        if (userId == null) throw new IllegalArgumentException("userId must be provided");
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("empty message");

        // if conversationId null -> create new
        Conversation conv = ensureConversation(userId, conversationId, null);
        if (!userId.equals(conv.getUserId())) throw new IllegalArgumentException("forbidden");

        // 1) save user message
        Message userMsg = new Message("user", text, Instant.now());
        userMsg.setConversation(conv);

        Message savedUserMsg;
        try {
            savedUserMsg = messageRepository.save(userMsg);
            // ensure conv relationship persisted
            if (!conv.getMessages().contains(savedUserMsg)) {
                conv.getMessages().add(savedUserMsg);
                conversationRepository.save(conv);
            }
        } catch (DataAccessException dae) {
            logger.error("DB error saving user message for userId={}, convId={} : {}", userId, conv.getId(), dae.getMessage(), dae);
            throw new RuntimeException("Database error saving message", dae);
        }

        // 2) call python upstream and capture raw response
        String rawUpstream;
        try {
            rawUpstream = callPython(text);
        } catch (RuntimeException e) {
            // mark user message as failed by appending tag (since no status field)
            try {
                savedUserMsg.setText(savedUserMsg.getText() + " [FAILED: upstream]");
                messageRepository.save(savedUserMsg);
            } catch (Exception ee) {
                logger.warn("Failed to mark user message as failed: {}", ee.getMessage(), ee);
            }
            logger.error("Upstream python call failed for userId={}, convId={}: {}", userId, conv.getId(), e.getMessage(), e);
            throw new RuntimeException("Upstream error: " + e.getMessage(), e);
        }

        // 3) parse upstream, extract best candidate reply and metadata
        String botText = rawUpstream;
        String metadataJson = null;
        String model = null;
        try {
            JsonNode root = objectMapper.readTree(rawUpstream);
            // heuristics: pick known fields if present
            if (root.has("reply")) {
                botText = root.get("reply").asText();
            } else if (root.has("hf_response") && root.get("hf_response").has("generated_text")) {
                botText = root.get("hf_response").get("generated_text").asText();
            } else if (root.has("generated_text")) {
                botText = root.get("generated_text").asText();
            } else {
                // leave botText as rawUpstream (string)
            }
            // keep raw JSON as metadata
            metadataJson = root.toString();

            // optional model detection
            if (root.has("model")) model = root.get("model").asText();
            // also check nested structures
            if (model == null && root.has("meta") && root.get("meta").has("model")) model = root.get("meta").get("model").asText();
        } catch (Exception ex) {
            // not JSON — raw text reply, metadata stays null
            metadataJson = null;
        }

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
            if (!conv.getMessages().contains(savedBotMsg)) {
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
