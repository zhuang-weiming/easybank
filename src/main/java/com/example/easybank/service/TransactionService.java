package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
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

@Service
@Slf4j
//@RequiredArgsConstructor
public class TransactionService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final RateLimiterService rateLimiterService;

    public TransactionService(AccountRepository accountRepository, 
                              TransactionRepository transactionRepository,
                              RateLimiterService rateLimiterService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.rateLimiterService = rateLimiterService;
    }
    
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
        log.info("Processing transaction from {} to {} for amount {}", sourceAccountNumber, destinationAccountNumber, amount);
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        
        // Generate a unique transaction ID for tracking
        String transactionId = generateTransactionId();
        log.info("Generated transaction ID: {}", transactionId);
        
        // Check for rate limiting first - this will throw TooManyRequestsException if the limit is exceeded
        rateLimiterService.checkTransactionRateLimit(sourceAccountNumber);
        
        // First, we create a transaction record with PENDING status
        Transaction transaction = new Transaction();
        transaction.setTransactionId(transactionId);
        transaction.setAmount(amount);
        transaction.setSourceAccountNumber(sourceAccountNumber);
        transaction.setDestinationAccountNumber(destinationAccountNumber);
        transaction.setTransactionType("TRANSFER");
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setDescription(String.format("Transfer %s %s from %s to %s", 
            amount.toString(), "USD", sourceAccountNumber, destinationAccountNumber));
        
        try {
            // Fetch accounts with pessimistic lock to prevent concurrent modifications
            Account sourceAccount = accountRepository.findByAccountNumberWithLock(sourceAccountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Source account not found: " + sourceAccountNumber));
            
            Account destinationAccount = accountRepository.findByAccountNumberWithLock(destinationAccountNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + destinationAccountNumber));
            
            // Validation steps
            validateAccounts(sourceAccount, destinationAccount, sourceAccountNumber, destinationAccountNumber);
            
            // Check sufficient funds
            if (sourceAccount.getBalance().compareTo(amount) < 0) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setDescription(transaction.getDescription() + " - Failed: Insufficient funds");
                transaction.setSourceAccount(sourceAccount);
                transaction.setDestinationAccount(destinationAccount);
                transactionRepository.save(transaction);
                throw new IllegalArgumentException("Insufficient funds in source account");
            }
            
            // Update transaction status to PROCESSING
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
            transactionRepository.save(transaction);
            
            log.info("Transaction {} completed successfully", transactionId);
            return transaction;
            
        } catch (OptimisticLockingFailureException e) {
            log.warn("Optimistic locking failure for transaction {}, will retry: {}", transactionId, e.getMessage());
            transaction.setStatus(TransactionStatus.RETRYING);
            transactionRepository.save(transaction);
            throw e; // Will be retried by Spring Retry
        } catch (Exception e) {
            log.error("Error processing transaction {}: {}", transactionId, e.getMessage(), e);
            // Only save the transaction record if it's not already saved
            if (transaction.getId() == null) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setDescription(transaction.getDescription() + " - Failed: " + e.getMessage());
                transactionRepository.save(transaction);
            } else {
                // Update existing transaction to FAILED
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
        log.error("All retries exhausted for transaction from {} to {} for amount {}", 
                 sourceAccountNumber, destinationAccountNumber, amount, e);
        
        // Create a failed transaction record for auditing and monitoring
        Transaction failedTransaction = new Transaction();
        failedTransaction.setTransactionId(generateTransactionId());
        failedTransaction.setAmount(amount);
        failedTransaction.setSourceAccountNumber(sourceAccountNumber);
        failedTransaction.setDestinationAccountNumber(destinationAccountNumber);
        failedTransaction.setStatus(TransactionStatus.FAILED);
        failedTransaction.setTransactionType("TRANSFER");
        failedTransaction.setDescription("Transfer failed after maximum retries: " + e.getMessage());
        
        // Try to save the failed transaction for audit purposes
        try {
            Account sourceAccount = accountRepository.findByAccountNumber(sourceAccountNumber).orElse(null);
            Account destAccount = accountRepository.findByAccountNumber(destinationAccountNumber).orElse(null);
            
            if (sourceAccount != null) {
                failedTransaction.setSourceAccount(sourceAccount);
                failedTransaction.setCurrency(sourceAccount.getCurrency());
            }
            
            if (destAccount != null) {
                failedTransaction.setDestinationAccount(destAccount);
            }
            
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
        
        // Ensure accounts have status
        if (sourceAccount.getStatus() == null) {
            sourceAccount.setStatus("ACTIVE");
        }
        
        if (destinationAccount.getStatus() == null) {
            destinationAccount.setStatus("ACTIVE");
        }
        
        // Check if accounts are active
        if (!"ACTIVE".equals(sourceAccount.getStatus())) {
            throw new IllegalArgumentException("Source account is not active");
        }
        
        if (!"ACTIVE".equals(destinationAccount.getStatus())) {
            throw new IllegalArgumentException("Destination account is not active");
        }
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
