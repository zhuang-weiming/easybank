package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
import com.example.easybank.domain.TransactionType;
import com.example.easybank.dto.TransactionResponse;
import com.example.easybank.repository.AccountRepository;
import com.example.easybank.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RateLimiterService rateLimiterService;

    @Cacheable(value = "accounts", key = "#accountNumber", unless = "#result == null")
    public Account getAccount(String accountNumber) {
        log.debug("Cache miss for account: {}", accountNumber);
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
    }
    
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Retryable(
        value = {OptimisticLockingFailureException.class, RuntimeException.class}, 
        maxAttempts = 3, 
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    @CacheEvict(value = "accounts", allEntries = true)
    public Transaction processTransaction(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount) {
        String transactionId = generateTransactionId();
        log.info("Processing transfer: {} -> {}, amount: {}, id: {}", 
                sourceAccountNumber, destinationAccountNumber, amount, transactionId);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        
        rateLimiterService.checkTransactionRateLimit(sourceAccountNumber);
        
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setAmount(amount);
        transaction.setSourceAccountNumber(sourceAccountNumber);
        transaction.setDestinationAccountNumber(destinationAccountNumber);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setDescription(String.format("Transfer %s USD from %s to %s", 
            amount.toString(), sourceAccountNumber, destinationAccountNumber));
        
        try {
            // Fetch accounts with pessimistic lock to prevent concurrent modifications
            Account sourceAccount = accountRepository.findByAccountNumberWithLock(sourceAccountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + sourceAccountNumber));
            
            Account destinationAccount = accountRepository.findByAccountNumberWithLock(destinationAccountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + destinationAccountNumber));
            
            // Validation steps - these should throw exceptions before any transaction is saved
            validateAccounts(sourceAccount, destinationAccount, sourceAccountNumber, destinationAccountNumber);
            
            // Check sufficient funds - this is a business rule validation, should throw before saving
            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient funds in source account");
            }
            
            // Only save the transaction after all validations pass
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction.setSourceAccount(sourceAccount);
            transaction.setDestinationAccount(destinationAccount);
            transaction.setCurrency(sourceAccount.getCurrency());
            transactionRepository.save(transaction);
            
            // Process the balance changes
            sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
            destinationAccount.setBalance(destinationAccount.getBalance().add(amount));
            
            // Update accounts in database
            accountRepository.update(sourceAccount);
            accountRepository.update(destinationAccount);
            
            // Update transaction to COMPLETED
            transaction.setStatus(TransactionStatus.COMPLETED);
            // Set account details for response
            transaction.setSourceAccountHolder(sourceAccount.getAccountHolder());
            transaction.setDestinationAccountHolder(destinationAccount.getAccountHolder());
            transactionRepository.save(transaction);
            
            log.info("Transaction {} completed successfully", transactionId);
            return transaction;
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure for transaction {}, will retry: {}", transactionId, e.getMessage());
            // Only save if we've already started processing
            if (transaction.getId() != null) {
                transaction.setStatus(TransactionStatus.RETRYING);
                transactionRepository.save(transaction);
            }
            throw e; // Will be retried by Spring Retry
        } catch (IllegalArgumentException e) {
            // Don't save validation failures
            log.error("Validation error for transaction {}: {}", transactionId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", transactionId, e.getMessage(), e);
            // Only save if we've already started processing
            if (transaction.getId() != null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setDescription(transaction.getDescription() + " - Failed: " + e.getMessage());
                transactionRepository.save(transaction);
            }
            throw e;
        }
    }
    
    /**
     * Recover method that gets called when all retries are exhausted
     */
    @Recover
    public Transaction recoverFromFailure(Exception e, String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount) {
        log.error("Transaction failed after retries: {} -> {}, amount: {}", 
                 sourceAccountNumber, destinationAccountNumber, amount);
        
        Transaction failedTransaction = new Transaction();
        failedTransaction.setTransactionId(generateTransactionId());
        failedTransaction.setAmount(amount);
        failedTransaction.setSourceAccountNumber(sourceAccountNumber);
        failedTransaction.setDestinationAccountNumber(destinationAccountNumber);
        failedTransaction.setStatus(TransactionStatus.FAILED);
        failedTransaction.setTransactionType(TransactionType.TRANSFER);
        failedTransaction.setDescription("Transfer failed: " + e.getMessage());
        
        try {
            transactionRepository.save(failedTransaction);
        } catch (Exception ex) {
            log.error("Failed to save failed transaction record", ex);
        }
        
        return failedTransaction;
    }
    
    private String generateTransactionId() {
        return UUID.randomUUID().toString();
    }
    
    private void validateAccounts(Account sourceAccount, Account destinationAccount, 
                                String sourceAccountNumber, String destinationAccountNumber) {
        // Validate required fields
        if (sourceAccount.getAccountHolder() == null || sourceAccount.getAccountType() == null) {
            throw new IllegalArgumentException("Source account is missing required fields");
        }
        if (destinationAccount.getAccountHolder() == null || destinationAccount.getAccountType() == null) {
            throw new IllegalArgumentException("Destination account is missing required fields");
        }
        
        // Set default values only if they are null
        if (sourceAccount.getAccountNumber() == null) {
            sourceAccount.setAccountNumber(sourceAccountNumber);
        }
        if (destinationAccount.getAccountNumber() == null) {
            destinationAccount.setAccountNumber(destinationAccountNumber);
        }
        
        if (sourceAccount.getStatus() == null || !"ACTIVE".equals(sourceAccount.getStatus())) {
            throw new IllegalArgumentException("Source account is not active");
        }
        if (destinationAccount.getStatus() == null || !"ACTIVE".equals(destinationAccount.getStatus())) {
            throw new IllegalArgumentException("Destination account is not active");
        }
    }
    
    public List<TransactionResponse> getAccountTransactions(String accountNumber) {
        // Get transactions with the latest status for each transaction ID
        List<Transaction> transactions = transactionRepository.findLatestTransactionsByAccountNumber(accountNumber);
        
        // Load associated accounts for each transaction
        for (Transaction transaction : transactions) {
            if (transaction.getSourceAccountId() != null) {
                accountRepository.findById(transaction.getSourceAccountId())
                    .ifPresent(account -> {
                        transaction.setSourceAccount(account);
                        transaction.setSourceAccountNumber(account.getAccountNumber());
                        transaction.setSourceAccountHolder(account.getAccountHolder());
                        // If this account is the source and amount is positive, negate it
                        if (accountNumber.equals(account.getAccountNumber()) && transaction.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                            transaction.setAmount(transaction.getAmount().negate());
                        }
                    });
            }
            if (transaction.getDestinationAccountId() != null) {
                accountRepository.findById(transaction.getDestinationAccountId())
                    .ifPresent(account -> {
                        transaction.setDestinationAccount(account);
                        transaction.setDestinationAccountNumber(account.getAccountNumber());
                        transaction.setDestinationAccountHolder(account.getAccountHolder());
                    });
            }
            
            // Ensure transactionId is set if null
            if (transaction.getTransactionId() == null) {
                transaction.setTransactionId(generateTransactionId());
            }
        }

        // Filter out intermediate states and convert to DTOs
        return transactions.stream()
            .filter(t -> t.getStatus() == TransactionStatus.COMPLETED || t.getStatus() == TransactionStatus.FAILED)
            .map(TransactionResponse::fromTransaction)
            .toList();
    }
}
