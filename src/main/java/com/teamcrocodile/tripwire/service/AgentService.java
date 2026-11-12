package com.teamcrocodile.tripwire.service;

import com.teamcrocodile.tripwire.model.Agent;

import java.util.List;

public interface AgentService {

    List<Agent> getAllAgents();
    Agent getAgentById(int id);
    Agent getAgentByEmail(String email);
    boolean authenticateAgent(String email, String password);
    void changePassword(int id, String currentPassword, String newPassword);
    void uploadProfilePicture(int id, String imageData);

    Agent createAgentAsAdmin(int adminId, String name, String email, String password);
    void deleteAgentAsAdmin(int adminId, int targetAgentId);
    void requireAdmin(int agentId);
}
