package com.example.user.user_microservice.controller;

import com.example.user.user_microservice.dto.UserDTO;
import com.example.user.user_microservice.dto.auth.GoogleLoginRequest;
import com.example.user.user_microservice.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    public AuthController(UserService userService) { this.userService = userService; }

    @PostMapping("/oauth/google")
    public ResponseEntity<UserDTO> googleLogin(@RequestBody GoogleLoginRequest req) {
        if (req == null || req.getIdToken() == null || req.getIdToken().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        UserDTO dto = userService.loginWithGoogleIdToken(req.getIdToken().trim());
        if (dto == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(dto);
    }
}