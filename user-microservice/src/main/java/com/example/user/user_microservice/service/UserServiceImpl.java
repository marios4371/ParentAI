package com.example.user.user_microservice.service;

import com.example.user.user_microservice.dto.UserDTO;
import com.example.user.user_microservice.dto.eventDTO.UserCreatedEvent;
import com.example.user.user_microservice.model.User;
import com.example.user.user_microservice.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;
    private final PasswordEncoder passwordEncoder;
    private final GoogleIdTokenVerifier googleVerifier;

    private final BCryptPasswordEncoder legacyBcrypt = new BCryptPasswordEncoder();
    private static final Pattern DELEGATING_PREFIX = Pattern.compile("^\\{[A-Za-z0-9_\\-]+\\}.*");

    public UserServiceImpl(UserRepository userRepository,
                           KafkaTemplate<String, UserCreatedEvent> kafkaTemplate,
                           PasswordEncoder passwordEncoder,
                           GoogleIdTokenVerifier googleVerifier) {
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.passwordEncoder = passwordEncoder;
        this.googleVerifier = googleVerifier;
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

        return new UserDTO(saved.getId(), saved.getUsername(), saved.getEmail(), null);
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
        return new UserDTO(user.getId(), user.getUsername(), user.getEmail(), null);
    }

    @Override
    @Transactional(readOnly = true)
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

    // Google ID Token login: verify → find-or-create → Kafka event on create
    @Override
    @Transactional
    public UserDTO loginWithGoogleIdToken(String idToken) {
        try {
            GoogleIdToken token = googleVerifier.verify(idToken);
            if (token == null) {
                return null; // πιθανός audience mismatch ή άκυρο token
            }
            Payload p = token.getPayload();

            String sub = p.getSubject(); // Google unique id
            String email = (String) p.get("email");
            boolean emailVerified = Boolean.TRUE.equals(p.getEmailVerified());
            String name = (String) p.get("name");
            String picture = (String) p.get("picture");

            // 1) Βρες με provider+providerId
            Optional<User> byProv = userRepository.findByProviderAndProviderId("google", sub);
            if (byProv.isPresent()) {
                User u = byProv.get();
                // προαιρετικές ενημερώσεις
                if (picture != null && (u.getAvatarUrl() == null || !picture.equals(u.getAvatarUrl()))) {
                    u.setAvatarUrl(picture);
                }
                if (name != null && (u.getUsername() == null || !name.equals(u.getUsername()))) {
                    u.setUsername(name);
                }
                if (u.getEmailVerified() == null || !u.getEmailVerified().equals(emailVerified)) {
                    u.setEmailVerified(emailVerified);
                }
                userRepository.save(u);
                return new UserDTO(u.getId(), u.getUsername(), u.getEmail(), null);
            }

            // 2) Αλλιώς, βρες με email και σύνδεσε Google
            Optional<User> byEmail = (email != null) ? userRepository.findByEmail(email) : Optional.empty();
            if (byEmail.isPresent()) {
                User u = byEmail.get();
                u.setProvider("google");
                u.setProviderId(sub);
                if (picture != null) u.setAvatarUrl(picture);
                if ((u.getUsername() == null || u.getUsername().isBlank()) && name != null) {
                    u.setUsername(name);
                }
                u.setEmailVerified(emailVerified);
                userRepository.save(u);
                return new UserDTO(u.getId(), u.getUsername(), u.getEmail(), null);
            }

            // 3) Δημιούργησε νέο χρήστη
            User nu = new User();
            nu.setUsername((name != null && !name.isBlank())
                    ? name
                    : (email != null ? email.split("@")[0] : ("google_" + sub.substring(0, 8))));
            nu.setEmail(email != null ? email : ("google+" + sub + "@users.noreply.local"));
            // placeholder password (δεν χρησιμοποιείται για login)
            nu.setPassword(passwordEncoder.encode("{google}" + UUID.randomUUID()));

            nu.setProvider("google");
            nu.setProviderId(sub);
            nu.setAvatarUrl(picture);
            nu.setEmailVerified(emailVerified);

            User saved = userRepository.save(nu);

            // Kafka event για νέο χρήστη
            UserCreatedEvent ev = new UserCreatedEvent();
            ev.setId(saved.getId());
            ev.setUsername(saved.getUsername());
            ev.setEmail(saved.getEmail());
            kafkaTemplate.send("users.created", String.valueOf(ev.getId()), ev);

            return new UserDTO(saved.getId(), saved.getUsername(), saved.getEmail(), null);
        } catch (Exception ex) {
            return null;
        }
    }
}