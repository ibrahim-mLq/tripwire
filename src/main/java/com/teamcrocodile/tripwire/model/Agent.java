package com.teamcrocodile.tripwire.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Agent {

    private int id;
    private String name;
    private String email;
    private String passwordHash;
    private String role = AgentRole.ANALYST.name();
    private String profilePicture;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Never serialised: the hash must not leave the server in any API response.
    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @JsonIgnore
    public AgentRole getAgentRole() {
        return AgentRole.fromString(role);
    }

    @JsonIgnore
    public boolean isAdmin() {
        return getAgentRole() == AgentRole.ADMIN;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }
}
