package com.teamcrocodile.tripwire.security;

import com.teamcrocodile.tripwire.service.AgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Rejects API requests that do not come from a logged-in agent.
 *
 * Login stores the agent's id in the server-side HTTP session. The browser only holds
 * the session cookie, so a caller cannot claim to be another agent or an admin by
 * changing a value in the request.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_AGENT_ID = "agentId";

    private final AgentService agentService;

    public AuthInterceptor(AgentService agentService) {
        this.agentService = agentService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object agentId = session != null ? session.getAttribute(SESSION_AGENT_ID) : null;

        // Also check the agent still exists, so removing a team member ends their access.
        if (agentId instanceof Integer id && agentService.getAgentById(id) != null) {
            return true;
        }
        if (session != null) {
            session.invalidate();
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("X-Auth-Required", "true");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Login required\",\"message\":\"Login required\"}");
        return false;
    }
}
