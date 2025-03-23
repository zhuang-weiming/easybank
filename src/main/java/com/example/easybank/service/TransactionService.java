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
        return accountRepository.findByAccountNumber(accountNumber)
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
        
        // Validate accounts have all required data
        if (sourceAccount.getAccountNumber() == null || destinationAccount.getAccountNumber() == null) {
            // Fix the account data if needed by forcing a direct database update
            if (sourceAccount.getAccountNumber() == null) {
                sourceAccount.setAccountNumber(sourceAccountNumber);
            }
            
            if (destinationAccount.getAccountNumber() == null) {
                destinationAccount.setAccountNumber(destinationAccountNumber);
            }
        }
        
        // Set default values for accountHolder and accountType only if they are null
        if (sourceAccount.getAccountHolder() == null) {
            // Try to look up the account by ID to get the correct account holder
            sourceAccount.setAccountHolder("Account Holder " + sourceAccountNumber);
        }
        
        if (sourceAccount.getAccountType() == null) {
            sourceAccount.setAccountType("CHECKING");
        }
        
        if (destinationAccount.getAccountHolder() == null) {
            destinationAccount.setAccountHolder("Account Holder " + destinationAccountNumber);
        }
        
        if (destinationAccount.getAccountType() == null) {
            destinationAccount.setAccountType("CHECKING");
        }
        
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in source account");
        }
        
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));
        
        accountRepository.update(sourceAccount);
        accountRepository.update(destinationAccount);
        
        Transaction transaction = new Transaction();
        transaction.setSourceAccount(sourceAccount);
        transaction.setDestinationAccount(destinationAccount);
        transaction.setAmount(amount);
        transaction.setCurrency(sourceAccount.getCurrency());
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setDescription(String.format("Transfer %s %s from %s to %s", 
            amount.toString(), sourceAccount.getCurrency(), sourceAccountNumber, destinationAccountNumber));
        
        transactionRepository.save(transaction);
        return transaction;
    }
    
    public List<Transaction> getAccountTransactions(String accountNumber) {
        List<Transaction> transactions = transactionRepository.findBySourceAccountAccountNumberOrDestinationAccountAccountNumber(accountNumber);
        
        // Populate the account information for each transaction
        for (Transaction transaction : transactions) {
            if (transaction.getSourceAccount() == null && transaction.getSourceAccountId() != null) {
                // Fetch the source account using the repository
                accountRepository.findById(transaction.getSourceAccountId())
                        .ifPresent(transaction::setSourceAccount);
            }
            
            if (transaction.getDestinationAccount() == null && transaction.getDestinationAccountId() != null) {
                // Fetch the destination account using the repository
                accountRepository.findById(transaction.getDestinationAccountId())
                        .ifPresent(transaction::setDestinationAccount);
            }
        }
        
        return transactions;
    }
}
