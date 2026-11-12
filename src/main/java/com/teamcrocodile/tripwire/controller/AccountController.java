package com.teamcrocodile.tripwire.controller;

import com.teamcrocodile.tripwire.dao.AccountDao;
import com.teamcrocodile.tripwire.model.Account;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of customer accounts for logged-in agents. Accounts are created by refund submissions. */
@RestController
@RequestMapping("/account")
public class AccountController {

    private final AccountDao accountDao;

    public AccountController(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @GetMapping
    public List<Account> getAllAccounts() {
        return accountDao.getAllAccounts();
    }

    @GetMapping("/{id}")
    public Account getAccountById(@PathVariable int id) {
        return accountDao.getAccountById(id);
    }
}
