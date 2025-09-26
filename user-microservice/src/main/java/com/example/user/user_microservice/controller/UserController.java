package com.example.user.user_microservice.controller;

import com.example.user.user_microservice.dto.UserDTO;
import com.example.user.user_microservice.model.User;
import com.example.user.user_microservice.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/auth")
@AllArgsConstructor
public class UserController {

    @Autowired
    private UserService userService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<?> saveUser(@RequestBody UserDTO userDto) {
        try {
            if (userDto.getUsername() == null || userDto.getEmail() == null || userDto.getPassword() == null) {
                return ResponseEntity.badRequest().body("Missing fields");
            }
            if(userService.isUserPresent(userDto)){
                return ResponseEntity.badRequest().body("User already exists!");
            }

            UserDTO saved = userService.saveUser(userDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO req) {
        if (req.getEmail() == null || req.getPassword() == null) {
            return ResponseEntity.badRequest().body("Missing fields");
        }

        User user = userService.authenticateAndMaybeMigrate(req.getEmail(), req.getPassword());
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");

        // FIX: return correct mapping (email in email field, password omitted)
        UserDTO out = new UserDTO(user.getId(), user.getUsername(), user.getEmail(), null);
        return ResponseEntity.ok(out);
    }

}