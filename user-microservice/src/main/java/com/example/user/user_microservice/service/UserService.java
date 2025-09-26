package com.example.user.user_microservice.service;

import com.example.user.user_microservice.dto.UserDTO;
import com.example.user.user_microservice.model.User;

public interface UserService {
    UserDTO saveUser(UserDTO userDto);
    boolean isUserPresent(UserDTO userDto);
    UserDTO findByEmail(String email);
    User authenticateAndMaybeMigrate(String email, String password);

    UserDTO loginWithGoogleIdToken(String idToken);


}
