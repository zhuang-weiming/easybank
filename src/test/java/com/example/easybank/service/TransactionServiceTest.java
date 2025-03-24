package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
import com.example.easybank.domain.TransactionType;
import com.example.easybank.dto.TransactionResponse;
import com.example.easybank.repository.AccountRepository;
import com.example.easybank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private TransactionService transactionService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;
    
    @Mock
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transactionService = new TransactionService(accountRepository, transactionRepository, rateLimiterService);
        
        // By default, allow rate limits for all tests
        doNothing().when(rateLimiterService).checkTransactionRateLimit(anyString());
    }

    @Test
    void processTransaction_SuccessfulTransfer() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setAccountHolder("John Doe");
        sourceAccount.setAccountType("SAVINGS");
        sourceAccount.setBalance(new BigDecimal("1000"));
        sourceAccount.setCurrency("USD");
        sourceAccount.setStatus("ACTIVE");

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setAccountHolder("Jane Smith");
        destinationAccount.setAccountType("CHECKING");
        destinationAccount.setBalance(new BigDecimal("500"));
        destinationAccount.setCurrency("USD");
        destinationAccount.setStatus("ACTIVE");

        when(accountRepository.findByAccountNumberWithLock("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("456")).thenReturn(Optional.of(destinationAccount));
        when(accountRepository.update(any(Account.class))).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(1);

        // Act
        Transaction result = transactionService.processTransaction("123", "456", new BigDecimal("100"));

        // Assert
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        assertEquals(new BigDecimal("900"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("600"), destinationAccount.getBalance());
        verify(accountRepository, times(1)).update(sourceAccount);
        verify(accountRepository, times(1)).update(destinationAccount);
        verify(rateLimiterService, times(1)).checkTransactionRateLimit("123");
    }

    @Test
    void processTransaction_InsufficientFunds() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setBalance(new BigDecimal("50"));
        sourceAccount.setStatus("ACTIVE");

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setBalance(new BigDecimal("500"));
        destinationAccount.setStatus("ACTIVE");

        when(accountRepository.findByAccountNumberWithLock("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("456")).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("100")));
        
        assertEquals(new BigDecimal("50"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("500"), destinationAccount.getBalance());
        verify(accountRepository, never()).update(any());
    }

    @Test
    void processTransaction_AccountNotFound() {
        // Arrange
        when(accountRepository.findByAccountNumberWithLock(any())).thenReturn(Optional.empty());
        // The implementation actually saves a transaction when account not found
        when(transactionRepository.save(any(Transaction.class))).thenReturn(1);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("100")));
        
        verify(accountRepository, never()).update(any());
        // Not verifying transaction save because the implementation DOES save a failed transaction
    }

    @Test
    void processTransaction_NegativeAmount() {
        // The implementation handles negative amount early and doesn't query repositories
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("-100")));
        
        verify(accountRepository, never()).findByAccountNumberWithLock(any());
        verify(accountRepository, never()).update(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void processTransaction_PreservesAccountHolderNames() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setAccountHolder("John Doe");
        sourceAccount.setAccountType("SAVINGS");
        sourceAccount.setBalance(new BigDecimal("1000"));
        sourceAccount.setCurrency("USD");
        sourceAccount.setStatus("ACTIVE");

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setAccountHolder("Jane Smith");
        destinationAccount.setAccountType("CHECKING");
        destinationAccount.setBalance(new BigDecimal("500"));
        destinationAccount.setCurrency("USD");
        destinationAccount.setStatus("ACTIVE");

        when(accountRepository.findByAccountNumberWithLock("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("456")).thenReturn(Optional.of(destinationAccount));
        when(accountRepository.update(any(Account.class))).thenReturn(1);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(1);

        // Act
        Transaction result = transactionService.processTransaction("123", "456", new BigDecimal("100"));

        // Assert
        assertEquals("John Doe", sourceAccount.getAccountHolder());
        assertEquals("Jane Smith", destinationAccount.getAccountHolder());
        assertEquals("SAVINGS", sourceAccount.getAccountType());
        assertEquals("CHECKING", destinationAccount.getAccountType());
        assertEquals(new BigDecimal("900"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("600"), destinationAccount.getBalance());
        verify(accountRepository, times(1)).update(sourceAccount);
        verify(accountRepository, times(1)).update(destinationAccount);
    }

    @Test
    void getAccountTransactions_LoadsAccountsForTransactions() {
        // Arrange
        String accountNumber = "123";
        
        // Create test transaction with IDs but no account objects
        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setSourceAccountId(10L);
        transaction.setDestinationAccountId(20L);
        transaction.setAmount(new BigDecimal("-100"));
        transaction.setCurrency("USD");
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.COMPLETED);
        
        // Create the accounts that should be loaded
        Account sourceAccount = new Account();
        sourceAccount.setId(10L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setAccountHolder("John Doe");
        
        Account destinationAccount = new Account();
        destinationAccount.setId(20L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setAccountHolder("Jane Smith");
        
        // Mock repository responses
        when(transactionRepository.findLatestTransactionsByAccountNumber(accountNumber))
            .thenReturn(List.of(transaction));
        when(accountRepository.findById(10L)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(20L)).thenReturn(Optional.of(destinationAccount));
        
        // Act
        List<TransactionResponse> transactions = transactionService.getAccountTransactions(accountNumber);
        
        // Assert
        assertNotNull(transactions);
        assertEquals(1, transactions.size());
        
        TransactionResponse response = transactions.get(0);
        assertEquals("123", response.getSourceAccountNumber());
        assertEquals("456", response.getDestinationAccountNumber());
        assertEquals("John Doe", response.getSourceAccountHolder());
        assertEquals("Jane Smith", response.getDestinationAccountHolder());
        assertEquals(new BigDecimal("-100"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals(TransactionType.TRANSFER, response.getTransactionType());
        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
    }

    @Test
    void processTransaction_RequiresAllFields() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setBalance(new BigDecimal("1000"));
        sourceAccount.setCurrency("USD");
        sourceAccount.setStatus("ACTIVE");

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setBalance(new BigDecimal("500"));
        destinationAccount.setCurrency("USD");
        destinationAccount.setStatus("ACTIVE");

        when(accountRepository.findByAccountNumberWithLock("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumberWithLock("456")).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            transactionService.processTransaction("123", "456", new BigDecimal("100"));
        });
        assertEquals("Source account is missing required fields", exception.getMessage());
        
        // Verify no updates were made
        verify(accountRepository, never()).update(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getAccountTransactions_HandlesNonExistentAccounts() {
        // Arrange
        String accountNumber = "123";
        
        // Create test transaction with IDs but no account objects
        Transaction transaction = new Transaction();
        transaction.setId(1L);
        transaction.setSourceAccountId(10L);
        transaction.setDestinationAccountId(20L);
        transaction.setAmount(new BigDecimal("-100"));
        transaction.setCurrency("USD");
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.COMPLETED);
        
        // Mock repository responses
        when(transactionRepository.findLatestTransactionsByAccountNumber(accountNumber))
            .thenReturn(List.of(transaction));
        when(accountRepository.findById(10L)).thenReturn(Optional.empty());
        when(accountRepository.findById(20L)).thenReturn(Optional.empty());
        
        // Act
        List<TransactionResponse> transactions = transactionService.getAccountTransactions(accountNumber);
        
        // Assert
        assertNotNull(transactions);
        assertEquals(1, transactions.size());
        
        TransactionResponse response = transactions.get(0);
        assertNull(response.getSourceAccountNumber());
        assertNull(response.getDestinationAccountNumber());
        assertNull(response.getSourceAccountHolder());
        assertNull(response.getDestinationAccountHolder());
        assertEquals(new BigDecimal("-100"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        assertEquals(TransactionType.TRANSFER, response.getTransactionType());
        assertEquals(TransactionStatus.COMPLETED, response.getStatus());
    }

    @Test
    void getAccountTransactions_HandlesEmptyTransactionsList() {
        // Arrange
        String accountNumber = "123";
        
        // Mock repository responses
        when(transactionRepository.findLatestTransactionsByAccountNumber(accountNumber))
            .thenReturn(List.of());
        
        // Act
        List<TransactionResponse> result = transactionService.getAccountTransactions(accountNumber);
        
        // Assert
        assertTrue(result.isEmpty());
        
        // Verify repository method was called
        verify(transactionRepository).findLatestTransactionsByAccountNumber(accountNumber);
        // Verify no account lookups were performed
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void getAccount_ReturnsAccount() {
        // Arrange
        String accountNumber = "123";
        Account account = new Account();
        account.setId(1L);
        account.setAccountNumber(accountNumber);
        account.setAccountHolder("John Doe");
        
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.of(account));
        
        // Act
        Account result = transactionService.getAccount(accountNumber);
        
        // Assert
        assertNotNull(result);
        assertEquals(accountNumber, result.getAccountNumber());
        assertEquals("John Doe", result.getAccountHolder());
        
        // Verify repository method was called
        verify(accountRepository).findByAccountNumber(accountNumber);
    }
    
    @Test
    void getAccount_ThrowsExceptionWhenAccountNotFound() {
        // Arrange
        String accountNumber = "nonexistent";
        when(accountRepository.findByAccountNumber(accountNumber)).thenReturn(Optional.empty());
        
        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, 
                                         () -> transactionService.getAccount(accountNumber));
        
        assertTrue(exception.getMessage().contains("Account not found"));
        
        // Verify repository method was called
        verify(accountRepository).findByAccountNumber(accountNumber);
    }
}
