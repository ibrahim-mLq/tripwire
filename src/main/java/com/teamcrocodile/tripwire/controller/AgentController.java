package com.teamcrocodile.tripwire.controller;

import com.teamcrocodile.tripwire.controller.dto.CreateAgentRequest;
import com.teamcrocodile.tripwire.exception.ForbiddenException;
import com.teamcrocodile.tripwire.model.Agent;
import com.teamcrocodile.tripwire.model.AgentRole;
import com.teamcrocodile.tripwire.security.AuthInterceptor;
import com.teamcrocodile.tripwire.service.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent accounts. Who the caller is always comes from the session
 * (set at login), never from an id sent in the request.
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials, HttpServletRequest request) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        if (!agentService.authenticateAgent(email, password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        Agent agent = agentService.getAgentByEmail(email);
        request.getSession(true);
        request.changeSessionId(); // new session id on login, so a pre-login id can't be reused
        request.getSession().setAttribute(AuthInterceptor.SESSION_AGENT_ID, agent.getId());
        return ResponseEntity.ok(toPublicAgent(agent));
    }

    @GetMapping("/team")
    public List<Map<String, Object>> getTeam(@SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        agentService.requireAdmin(currentAgentId);
        return agentService.getAllAgents().stream()
                .map(this::toPublicAgent)
                .toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> getAgentById(@PathVariable int id,
                                            @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        requireSelf(id, currentAgentId);
        return toPublicAgent(agentService.getAgentById(id));
    }

    @PostMapping("/admin/create")
    public ResponseEntity<?> createAgentAsAdmin(@RequestBody CreateAgentRequest request,
                                                @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        Agent created = agentService.createAgentAsAdmin(
                currentAgentId,
                request.getName(),
                request.getEmail(),
                request.getPassword()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toPublicAgent(created));
    }

    @DeleteMapping("/admin/{id}")
    public Map<String, String> deleteAgentAsAdmin(@PathVariable int id,
                                                  @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        agentService.deleteAgentAsAdmin(currentAgentId, id);
        return Map.of("message", "Team member removed");
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<?> changePassword(@PathVariable int id,
                                            @RequestBody ChangePasswordRequest request,
                                            @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        requireSelf(id, currentAgentId);
        try {
            agentService.changePassword(id, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{id}/profile-picture")
    public Map<String, String> uploadProfilePicture(@PathVariable int id,
                                                    @RequestBody Map<String, String> request,
                                                    @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        requireSelf(id, currentAgentId);
        agentService.uploadProfilePicture(id, request.get("imageData"));
        return Map.of("message", "Profile picture updated successfully");
    }

    private void requireSelf(int id, int currentAgentId) {
        if (id != currentAgentId) {
            throw new ForbiddenException("You can only view or change your own account");
        }
    }

    /** The only shape an agent is ever returned in: no password hash. */
    private Map<String, Object> toPublicAgent(Agent agent) {
        AgentRole role = agent.getAgentRole();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", agent.getId());
        body.put("name", agent.getName());
        body.put("email", agent.getEmail());
        body.put("role", role.name());
        body.put("roleLabel", role.displayLabel());
        body.put("profilePicture", agent.getProfilePicture() != null ? agent.getProfilePicture() : "");
        return body;
    }

    public static class ChangePasswordRequest {
        private String currentPassword;
        private String newPassword;

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}
