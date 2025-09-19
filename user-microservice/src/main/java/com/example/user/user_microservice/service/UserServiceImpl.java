package com.example.user.user_microservice.service;

import com.example.user.user_microservice.dto.UserDTO;
import com.example.user.user_microservice.dto.eventDTO.UserCreatedEvent;
import com.example.user.user_microservice.model.User;
import com.example.user.user_microservice.repository.UserRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;
    private final PasswordEncoder passwordEncoder;
    private final BCryptPasswordEncoder legacyBcrypt = new BCryptPasswordEncoder();
    private static final Pattern DELEGATING_PREFIX = Pattern.compile("^\\{[A-Za-z0-9_\\-]+\\}.*");

    public UserServiceImpl(UserRepository userRepository,
                           KafkaTemplate<String, UserCreatedEvent> kafkaTemplate,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserDTO saveUser(UserDTO userDto) {
        String rawOrEncoded = userDto.getPassword().trim();
        String hashed;
        if (rawOrEncoded.startsWith("{") || rawOrEncoded.startsWith("$2a$") ||
                rawOrEncoded.startsWith("$2b$") || rawOrEncoded.startsWith("$2y$")) {
            hashed = rawOrEncoded; // assume already encoded
        } else {
            hashed = passwordEncoder.encode(rawOrEncoded);
        }

        User user = new User();
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPassword(hashed);

        User saved = userRepository.save(user);

        UserCreatedEvent ev = new UserCreatedEvent();
        ev.setId(saved.getId());
        ev.setUsername(saved.getUsername());
        ev.setEmail(saved.getEmail());
        kafkaTemplate.send("users.created", String.valueOf(ev.getId()), ev);

        return new UserDTO(saved.getId(), saved.getUsername(), null, saved.getEmail());
    }

    @Override
    public boolean isUserPresent(UserDTO userDto) {
        if (userDto == null) return false;
        return userRepository.findByUsername(userDto.getUsername()).isPresent();
    }

    @Override
    public UserDTO findByEmail(String email) {
        Optional<User> u = userRepository.findByEmail(email);
        if (u.isEmpty()) return null;
        User user = u.get();
        return new UserDTO(user.getId(), user.getUsername(), null, user.getEmail());
    }
    @Transactional
    public User authenticateAndMaybeMigrate(String email, String rawPassword) {
        Optional<User> opt = userRepository.findByEmail(email);
        if (opt.isEmpty()) return null;
        User user = opt.get();

        String stored = user.getPassword();
        if (stored == null) return null;
        stored = stored.trim();

        if (stored.isEmpty()) return null;

        if (DELEGATING_PREFIX.matcher(stored).matches()) {
            try {
                boolean ok = passwordEncoder.matches(rawPassword, stored);
                return ok ? user : null;
            } catch (Exception ex) {
                return null;
            }
        }

        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            boolean legacyOk = false;
            try {
                legacyOk = legacyBcrypt.matches(rawPassword, stored);
            } catch (Exception ex) {
                legacyOk = false;
            }
            if (!legacyOk) return null;

            String newHash = passwordEncoder.encode(rawPassword);
            user.setPassword(newHash);
            userRepository.save(user);
            return user;
        }

        try {
            if (passwordEncoder.matches(rawPassword, stored)) return user;
        } catch (Exception ignored) {}

        return null;
    }
}
