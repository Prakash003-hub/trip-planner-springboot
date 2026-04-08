package com.travel.api.dto;

public class AuthenticationRequest {
    private String identifier; // email or userId
    private String password;
    private String groupId;

    public AuthenticationRequest() {
    }

    public AuthenticationRequest(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public AuthenticationRequest(String identifier, String password, String groupId) {
        this.identifier = identifier;
        this.password = password;
        this.groupId = groupId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
}
