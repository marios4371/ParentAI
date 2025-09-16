package com.example.user.user_microservice.service;

import com.example.user.user_microservice.dto.UserDTO;
import com.example.user.user_microservice.dto.eventDTO.UserCreatedEvent;
import com.example.user.user_microservice.model.User;
import com.example.user.user_microservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService{

    @Autowired
    UserRepository userRepository;

    @Autowired
    private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @Override
    public UserDTO saveUser(UserDTO userDto) {
        User user = new User(
                userDto.getId(),
                userDto.getUsername(),
                userDto.getEmail(),
                userDto.getPassword()
        );


        User savedUser = userRepository.save(user);

        UserDTO savedUserDto = new UserDTO(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getPassword(),
                savedUser.getEmail()
        );

        UserCreatedEvent userCreatedEvent = new UserCreatedEvent();
        userCreatedEvent.setId(savedUser.getId());
        userCreatedEvent.setUsername(savedUser.getUsername());
        userCreatedEvent.setEmail(savedUser.getEmail());

        kafkaTemplate.send("users.created", String.valueOf(userCreatedEvent.getId()), userCreatedEvent)
                .addCallback(
                        result -> { /* success logging */ },
                        ex -> { /* failure logging or retry */ }
                );


        return savedUserDto;
    }

    @Override
    public boolean isUserPresent(UserDTO userDto) {
        User currUser = new User(
                userDto.getId(),
                userDto.getUsername(),
                userDto.getEmail(),
                userDto.getPassword()
        );
        Optional<User> currUserDto = userRepository.findByUsername(currUser.getUsername());
        return currUserDto.isPresent();
    }

    @Override
    public UserDTO findByEmail(String email) {
        Optional<User> u = userRepository.findByEmail(email);
        if (u.isEmpty()) return null;
        User user = u.get();
        return new UserDTO(user.getId(), user.getUsername(), null, user.getEmail());
    }
}
