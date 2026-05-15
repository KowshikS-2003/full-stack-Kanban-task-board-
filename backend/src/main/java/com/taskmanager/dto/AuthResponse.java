package com.taskmanager.dto;

public class AuthResponse {

    private final String token;
    private final String username;
    private final Long   userId;

    public AuthResponse(String token, String username, Long userId) {
        this.token    = token;
        this.username = username;
        this.userId   = userId;
    }

    public String getToken()    { return token; }
    public String getUsername() { return username; }
    public Long   getUserId()   { return userId; }
}
