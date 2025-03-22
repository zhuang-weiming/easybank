package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(String accountHolder, String accountType, String currency, BigDecimal initialBalance) {
        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setAccountHolder(accountHolder);
        account.setAccountType(accountType);
        account.setCurrency(currency);
        account.setBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO);
        account.setVersion(0);
        account.setStatus("ACTIVE");
        
        accountRepository.save(account);
        return account;
    }

    private String generateAccountNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
