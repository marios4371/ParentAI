package com.example.user.user_microservice.model;

import javax.persistence.*;

@Entity
@Table(name = "user", indexes = {
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_provider_pid", columnList = "provider,provider_id")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private long id;

    @Column(name = "username", length = 150)
    private String username;

    @Column(name = "email", length = 190, unique = false)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    // Νέα πεδία για OAuth
    @Column(name = "provider", length = 30)
    private String provider; // "google"

    @Column(name = "provider_id", length = 190)
    private String providerId; // Google sub

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    public User(){}
    public User(long id, String username, String email, String password) {
        this.id = id; this.username = username; this.email = email; this.password = password;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
}