package com.travel.api.dto;

public class AuthenticationResponse {
    private String token;
    private String name;
    private String userId;
    private String email;
    private Long tripId;

    public AuthenticationResponse() {
    }

    public AuthenticationResponse(String token, String name, String userId, String email) {
        this.token = token;
        this.name = name;
        this.userId = userId;
        this.email = email;
    }

    public AuthenticationResponse(String token, String name, String userId, String email, Long tripId) {
        this.token = token;
        this.name = name;
        this.userId = userId;
        this.email = email;
        this.tripId = tripId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }
}
