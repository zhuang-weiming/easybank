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
        
        // Set timestamps
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        
        accountRepository.save(account);
        return account;
    }

    private String generateAccountNumber() {
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "ACC-" + randomPart;
    }

    @Transactional(readOnly = true)
    public Account getAccount(String accountNumber) {
        // Normalize account number format if needed
        String normalizedAccountNumber = normalizeAccountNumber(accountNumber);
        return accountRepository.findByAccountNumber(normalizedAccountNumber)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("Account not found: %s. Please ensure the account number is in the format ACC-xxxxxxxx", accountNumber)));
    }

    private String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        // If it doesn't start with ACC-, add it
        if (!accountNumber.startsWith("ACC-")) {
            return "ACC-" + accountNumber;
        }
        return accountNumber;
    }
}
