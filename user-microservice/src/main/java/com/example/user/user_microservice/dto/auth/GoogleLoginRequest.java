package com.example.user.user_microservice.dto.auth;

public class GoogleLoginRequest {
    private String idToken;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}