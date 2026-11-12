package com.teamcrocodile.tripwire.controller;

import com.teamcrocodile.tripwire.client.dto.DymoResponse;
import com.teamcrocodile.tripwire.exception.ForbiddenException;
import com.teamcrocodile.tripwire.model.Agent;
import com.teamcrocodile.tripwire.model.Transaction;
import com.teamcrocodile.tripwire.security.AuthInterceptor;
import com.teamcrocodile.tripwire.service.AgentService;
import com.teamcrocodile.tripwire.service.TransactionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Refund cases. All routes need a logged-in agent (see WebConfig). */
@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;
    private final AgentService agentService;

    public TransactionController(TransactionService transactionService, AgentService agentService) {
        this.transactionService = transactionService;
        this.agentService = agentService;
    }

    @GetMapping("/transactions")
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @GetMapping("/{id}")
    public Transaction getTransactionById(@PathVariable int id) {
        return transactionService.getTransactionById(id);
    }

    @PostMapping("/add")
    public Transaction addTransaction(@RequestBody Transaction transaction) {
        return transactionService.createTransaction(transaction);
    }

    // Record a decision or note. Only the agent the case is assigned to (or an admin) may do this.
    @PutMapping("/{id}")
    public Transaction updateTransaction(@PathVariable int id,
                                         @RequestBody Transaction transaction,
                                         @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        Transaction current = transactionService.getTransactionById(id);
        boolean isAssignee = current.getAgentId() != null && current.getAgentId() == currentAgentId;
        if (!isAssignee && !isAdmin(currentAgentId)) {
            throw new ForbiddenException("Only the agent assigned to this case can update it");
        }

        transaction.setId(id); // the path id is authoritative
        transactionService.updateTransaction(transaction);
        return transactionService.getTransactionById(id);
    }

    @PostMapping("/{id}/score")
    public DymoResponse scoreTransaction(@PathVariable int id) {
        return transactionService.scoreTransaction(id);
    }

    @DeleteMapping("/{id}")
    public void deleteTransaction(@PathVariable int id,
                                  @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        agentService.requireAdmin(currentAgentId);
        transactionService.deleteTransaction(id);
    }

    // Assign an unassigned case: sets agent_id and moves status to UNDER_REVIEW.
    // Analysts can only assign to themselves; an admin can assign to anyone.
    @PutMapping("/{id}/assign/{agentId}")
    public Transaction assignTransaction(@PathVariable int id,
                                         @PathVariable int agentId,
                                         @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        requireSelfOrAdmin(agentId, currentAgentId);
        return transactionService.assignTransaction(id, agentId);
    }

    // Hand a case back: clears agent_id and sets status to UNASSIGNED.
    @PutMapping("/{id}/unassign/{agentId}")
    public Transaction unassignTransaction(@PathVariable int id,
                                           @PathVariable int agentId,
                                           @SessionAttribute(AuthInterceptor.SESSION_AGENT_ID) int currentAgentId) {
        requireSelfOrAdmin(agentId, currentAgentId);
        return transactionService.unassignTransaction(id, agentId);
    }

    @GetMapping("/agent/{agentId}")
    public List<Transaction> getTransactionsByAgent(@PathVariable int agentId) {
        return transactionService.getTransactionsByAgentId(agentId);
    }

    // Full transaction history for an account (used by the case detail page)
    @GetMapping("/account/{accountId}")
    public List<Transaction> getTransactionsByAccount(@PathVariable int accountId) {
        return transactionService.getTransactionsByAccountId(accountId);
    }

    private void requireSelfOrAdmin(int agentId, int currentAgentId) {
        if (agentId != currentAgentId && !isAdmin(currentAgentId)) {
            throw new ForbiddenException("You can only assign or unassign cases for yourself");
        }
    }

    private boolean isAdmin(int agentId) {
        Agent agent = agentService.getAgentById(agentId);
        return agent != null && agent.isAdmin();
    }
}
