package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.domain.TransactionStatus;
import com.example.easybank.repository.AccountRepository;
import com.example.easybank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

    private TransactionService transactionService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        transactionService = new TransactionService(accountRepository, transactionRepository);
    }

    @Test
    void processTransaction_SuccessfulTransfer() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setBalance(new BigDecimal("1000"));
        sourceAccount.setCurrency("USD");

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setBalance(new BigDecimal("500"));
        destinationAccount.setCurrency("USD");

        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("456")).thenReturn(Optional.of(destinationAccount));
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
    }

    @Test
    void processTransaction_InsufficientFunds() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setId(1L);
        sourceAccount.setAccountNumber("123");
        sourceAccount.setBalance(new BigDecimal("50"));

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setBalance(new BigDecimal("500"));

        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("456")).thenReturn(Optional.of(destinationAccount));

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
        when(accountRepository.findByAccountNumber(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("100")));
        
        verify(accountRepository, never()).update(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void processTransaction_NegativeAmount() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("-100")));
        
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

        Account destinationAccount = new Account();
        destinationAccount.setId(2L);
        destinationAccount.setAccountNumber("456");
        destinationAccount.setAccountHolder("Jane Smith");
        destinationAccount.setAccountType("CHECKING");
        destinationAccount.setBalance(new BigDecimal("500"));
        destinationAccount.setCurrency("USD");

        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("456")).thenReturn(Optional.of(destinationAccount));
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
}
