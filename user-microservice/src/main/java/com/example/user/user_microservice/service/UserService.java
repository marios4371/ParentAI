package com.example.user.user_microservice.service;

import com.example.user.user_microservice.dto.UserDTO;
public interface UserService {
    UserDTO saveUser(UserDTO userDto);
    boolean isUserPresent(UserDTO userDto);

    UserDTO findByEmail(String email);


}
