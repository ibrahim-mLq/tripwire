package com.teamcrocodile.tripwire.controller;

import com.teamcrocodile.tripwire.dao.AccountDao;
import com.teamcrocodile.tripwire.model.Account;
import com.teamcrocodile.tripwire.model.Transaction;
import com.teamcrocodile.tripwire.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/refund")
public class RefundController {

    private final AccountDao accountDao;
    private final TransactionService transactionService;

    public RefundController(AccountDao accountDao, TransactionService transactionService) {
        this.accountDao = accountDao;
        this.transactionService = transactionService;
    }

    /**
     * Customer-facing endpoint: creates an Account and a linked Transaction in one request.
     * Public, so it only hands back a case reference. The risk score and status stay internal.
     */
    @PostMapping("/submit")
    public RefundReceipt submitRefund(@RequestBody RefundSubmitRequest req,
                                      HttpServletRequest httpRequest) {
        validate(req);

        // Use the address of the actual connection. X-Forwarded-For is set by the client, so
        // trusting it would let a fraudster choose the IP that feeds their own risk score.
        // Behind a reverse proxy, set server.forward-headers-strategy so this resolves correctly.
        String ip = httpRequest.getRemoteAddr();

        Account account = new Account();
        account.setName(req.getCustomerName());
        account.setEmail(req.getEmail());
        account.setPhoneNumber(req.getPhone());
        account.setIban(req.getIban());
        account.setIpAddress(ip);
        Account saved = accountDao.createAccount(account);

        Transaction tx = new Transaction();
        tx.setAccountId(saved.getId());
        tx.setAmount(req.getAmount());
        tx.setCurrency(req.getCurrency() != null ? req.getCurrency() : "USD");
        tx.setOrderId(req.getOrderId());
        tx.setOrderDate(req.getOrderDate());
        tx.setItems(req.getItems());
        tx.setPaymentMethod(req.getPaymentMethod());
        tx.setShippingAddress(req.getShippingAddress());
        tx.setBillingAddress(req.getBillingAddress());
        tx.setReasonText(req.getReasonText());

        Transaction created = transactionService.createTransaction(tx);
        return new RefundReceipt(created.getId());
    }

    private void validate(RefundSubmitRequest req) {
        if (req.getCustomerName() == null || req.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (req.getEmail() == null || !req.getEmail().contains("@")) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero");
        }
    }

    public record RefundReceipt(int id) {}

    // ── Request DTO ──────────────────────────────────────────────────────────
    public static class RefundSubmitRequest {
        private String customerName;
        private String email;
        private String phone;
        private String iban;

        private BigDecimal amount;
        private String currency;
        private String orderId;
        private String orderDate;
        private String items;
        private String paymentMethod;
        private String shippingAddress;
        private String billingAddress;
        private String reasonText;

        public String getCustomerName()     { return customerName; }
        public void setCustomerName(String v){ this.customerName = v; }
        public String getEmail()             { return email; }
        public void setEmail(String v)       { this.email = v; }
        public String getPhone()             { return phone; }
        public void setPhone(String v)       { this.phone = v; }
        public String getIban()              { return iban; }
        public void setIban(String v)        { this.iban = v; }
        public BigDecimal getAmount()        { return amount; }
        public void setAmount(BigDecimal v)  { this.amount = v; }
        public String getCurrency()          { return currency; }
        public void setCurrency(String v)    { this.currency = v; }
        public String getOrderId()           { return orderId; }
        public void setOrderId(String v)     { this.orderId = v; }
        public String getOrderDate()         { return orderDate; }
        public void setOrderDate(String v)   { this.orderDate = v; }
        public String getItems()             { return items; }
        public void setItems(String v)       { this.items = v; }
        public String getPaymentMethod()     { return paymentMethod; }
        public void setPaymentMethod(String v){ this.paymentMethod = v; }
        public String getShippingAddress()   { return shippingAddress; }
        public void setShippingAddress(String v){ this.shippingAddress = v; }
        public String getBillingAddress()    { return billingAddress; }
        public void setBillingAddress(String v){ this.billingAddress = v; }
        public String getReasonText()        { return reasonText; }
        public void setReasonText(String v)  { this.reasonText = v; }
    }
}

