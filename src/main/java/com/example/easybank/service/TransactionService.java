package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
import com.example.easybank.repository.AccountRepository;
import com.example.easybank.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
//@RequiredArgsConstructor
public class TransactionService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }
    
    @Cacheable(value = "accounts", key = "#accountNumber")
    public Account getAccount(String accountNumber) {
        return accountRepository.findById(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Retryable(value = {RuntimeException.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    @CacheEvict(value = "accounts", allEntries = true)
    public Transaction processTransaction(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount) {
        log.info("Processing transaction from {} to {} for amount {}", sourceAccountNumber, destinationAccountNumber, amount);
        
        Account sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + sourceAccountNumber));
        
        Account destinationAccount = accountRepository.findByAccountNumber(destinationAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + destinationAccountNumber));
        
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in source account");
        }
        
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));
        
        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);
        
        Transaction transaction = new Transaction();
        transaction.setSourceAccount(sourceAccount);
        transaction.setDestinationAccount(destinationAccount);
        transaction.setAmount(amount);
        transaction.setCurrency(sourceAccount.getCurrency());
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(String.format("Transfer %s %s from %s to %s", 
            amount.toString(), sourceAccount.getCurrency(), sourceAccountNumber, destinationAccountNumber));
        
        return transactionRepository.save(transaction);
    }
    
    public List<Transaction> getFailedTransactions() {
        return transactionRepository.findByStatus(TransactionStatus.FAILED);
    }
    
    public List<Transaction> getRetryableTransactions(int maxRetries) {
        return transactionRepository.findRetryableTransactions(TransactionStatus.FAILED, maxRetries);
    }
    
    public List<Transaction> getAccountTransactions(String accountNumber) {
        return transactionRepository.findBySourceAccountAccountNumberOrDestinationAccountAccountNumber(
                accountNumber, accountNumber);
    }

    public AccountRepository getAccountRepository() {
        return accountRepository;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }
}
