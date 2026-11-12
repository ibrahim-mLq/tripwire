package com.teamcrocodile.tripwire.controller;

import com.teamcrocodile.tripwire.dao.AccountDao;
import com.teamcrocodile.tripwire.dao.AgentDao;
import com.teamcrocodile.tripwire.dao.TransactionDao;
import com.teamcrocodile.tripwire.model.Account;
import com.teamcrocodile.tripwire.model.Agent;
import com.teamcrocodile.tripwire.model.AgentRole;
import com.teamcrocodile.tripwire.model.Status;
import com.teamcrocodile.tripwire.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of who is allowed to do what, through the real HTTP layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiSecurityTest {

    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mvc;
    @Autowired private AgentDao agentDao;
    @Autowired private AccountDao accountDao;
    @Autowired private TransactionDao transactionDao;

    private Agent admin;
    private Agent analyst;
    private Agent otherAnalyst;

    @BeforeEach
    void setUp() {
        admin = createAgent("Admin", "admin@test.com", AgentRole.ADMIN);
        analyst = createAgent("Analyst", "analyst@test.com", AgentRole.ANALYST);
        otherAnalyst = createAgent("Other", "other@test.com", AgentRole.ANALYST);
    }

    @Test
    void apiRejectsRequestsWithoutLogin() throws Exception {
        mvc.perform(get("/transaction/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/agent/team")).andExpect(status().isUnauthorized());
        mvc.perform(get("/account")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/transaction/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/agent/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"analyst@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginOpensSessionAndLogoutEndsIt() throws Exception {
        MockHttpSession session = login(analyst);
        mvc.perform(get("/transaction/transactions").session(session)).andExpect(status().isOk());

        mvc.perform(post("/auth/logout").session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/transaction/transactions").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordHashNeverAppearsInResponses() throws Exception {
        mvc.perform(post("/agent/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@test.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("asswordHash"))));

        mvc.perform(get("/agent/team").session(login(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andExpect(content().string(not(containsString("asswordHash"))));
    }

    @Test
    void analystCannotUseAdminEndpointsEvenWhenClaimingAnAdminId() throws Exception {
        MockHttpSession session = login(analyst);

        // The old API trusted this parameter. It is now ignored: identity comes from the session.
        mvc.perform(get("/agent/team").param("adminId", String.valueOf(admin.getId())).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/agent/admin/create").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminId\":" + admin.getId()
                                + ",\"name\":\"Eve\",\"email\":\"eve@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/agent/admin/" + otherAnalyst.getId()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageTheTeam() throws Exception {
        MockHttpSession session = login(admin);

        mvc.perform(get("/agent/team").session(session)).andExpect(status().isOk());
        mvc.perform(post("/agent/admin/create").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Hire\",\"email\":\"new@test.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ANALYST"));
    }

    @Test
    void agentCannotChangeAnotherAgentsPassword() throws Exception {
        mvc.perform(put("/agent/" + otherAnalyst.getId() + "/password").session(login(analyst))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"hijacked123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void finalisedCaseCannotBeReopenedByAssigningIt() throws Exception {
        Transaction approved = createCase(Status.APPROVED, analyst.getId());

        mvc.perform(put("/transaction/" + approved.getId() + "/assign/" + analyst.getId()).session(login(analyst)))
                .andExpect(status().isConflict());

        assertEquals(Status.APPROVED, transactionDao.getTransactionById(approved.getId()).getStatus());
    }

    @Test
    void analystCanClaimAnUnassignedCaseButNotForSomeoneElse() throws Exception {
        Transaction unassigned = createCase(Status.UNASSIGNED, null);
        MockHttpSession session = login(analyst);

        mvc.perform(put("/transaction/" + unassigned.getId() + "/assign/" + otherAnalyst.getId()).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(put("/transaction/" + unassigned.getId() + "/assign/" + analyst.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    @Test
    void onlyTheAssignedAgentCanDecideACase() throws Exception {
        Transaction underReview = createCase(Status.UNDER_REVIEW, analyst.getId());
        String decision = "{\"status\":\"APPROVED\",\"amount\":1.00}";

        mvc.perform(put("/transaction/" + underReview.getId()).session(login(otherAnalyst))
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isForbidden());

        mvc.perform(put("/transaction/" + underReview.getId()).session(login(analyst))
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                // the amount in the request is ignored; a decision cannot change the refund value
                .andExpect(jsonPath("$.amount").value(250.00));
    }

    @Test
    void onlyAdminCanDeleteACase() throws Exception {
        Transaction c = createCase(Status.UNASSIGNED, null);

        mvc.perform(delete("/transaction/" + c.getId()).session(login(analyst))).andExpect(status().isForbidden());
        mvc.perform(delete("/transaction/" + c.getId()).session(login(admin))).andExpect(status().isOk());
    }

    @Test
    void refundSubmissionIsPublicReturnsOnlyAReferenceAndIgnoresSpoofedIp() throws Exception {
        MvcResult result = mvc.perform(post("/refund/submit")
                        .header("X-Forwarded-For", "8.8.8.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Jo Bloggs\",\"email\":\"jo@example.com\",\"amount\":49.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.riskScore").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andReturn();

        int caseId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Transaction created = transactionDao.getTransactionById(caseId);
        Account account = accountDao.getAccountById(created.getAccountId());
        assertEquals("127.0.0.1", account.getIpAddress());
    }

    @Test
    void refundSubmissionWithoutAmountIsABadRequest() throws Exception {
        mvc.perform(post("/refund/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Jo Bloggs\",\"email\":\"jo@example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login(Agent agent) throws Exception {
        MvcResult result = mvc.perform(post("/agent/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + agent.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Agent createAgent(String name, String email, AgentRole role) {
        Agent agent = new Agent();
        agent.setName(name);
        agent.setEmail(email);
        agent.setPasswordHash("{noop}" + PASSWORD);
        agent.setRole(role.name());
        return agentDao.createAgent(agent);
    }

    private Transaction createCase(Status status, Integer agentId) {
        Account account = new Account();
        account.setName("Customer");
        account.setEmail("customer@example.com");
        account.setIpAddress("192.168.1.1");
        accountDao.createAccount(account);

        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        transaction.setAgentId(agentId);
        transaction.setAmount(new BigDecimal("250.00"));
        transaction.setCurrency("GBP");
        transaction.setRiskScore(40.0);
        transaction.setStatus(status);
        return transactionDao.createTransaction(transaction);
    }
}
