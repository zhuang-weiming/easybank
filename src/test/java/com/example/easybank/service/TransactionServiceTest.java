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
        sourceAccount.setAccountNumber("123");
        sourceAccount.setBalance(new BigDecimal("1000"));

        Account destinationAccount = new Account();
        destinationAccount.setAccountNumber("456");
        destinationAccount.setBalance(new BigDecimal("500"));

        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("456")).thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        Transaction result = transactionService.processTransaction("123", "456", new BigDecimal("100"));

        // Assert
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        assertEquals(new BigDecimal("900"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("600"), destinationAccount.getBalance());
        verify(accountRepository, times(1)).save(sourceAccount);
        verify(accountRepository, times(1)).save(destinationAccount);
    }

    @Test
    void processTransaction_InsufficientFunds() {
        // Arrange
        Account sourceAccount = new Account();
        sourceAccount.setAccountNumber("123");
        sourceAccount.setBalance(new BigDecimal("50"));

        Account destinationAccount = new Account();
        destinationAccount.setAccountNumber("456");
        destinationAccount.setBalance(new BigDecimal("500"));

        when(accountRepository.findByAccountNumber("123")).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findByAccountNumber("456")).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("100")));
        
        assertEquals(new BigDecimal("50"), sourceAccount.getBalance());
        assertEquals(new BigDecimal("500"), destinationAccount.getBalance());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void processTransaction_AccountNotFound() {
        // Arrange
        when(accountRepository.findByAccountNumber(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("100")));
        
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void processTransaction_NegativeAmount() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> transactionService.processTransaction("123", "456", new BigDecimal("-100")));
        
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }
}
