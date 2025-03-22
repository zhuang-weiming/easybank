package com.example.easybank.service;

import com.example.easybank.domain.Account;
import com.example.easybank.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        accountService = new AccountService(accountRepository);
    }

    @Test
    void createAccount_WithValidData_ShouldReturnCreatedAccount() {
        // Arrange
        String accountHolder = "John Doe";
        String accountType = "SAVINGS";
        String currency = "USD";
        BigDecimal initialBalance = new BigDecimal("1000.00");

        Account expectedAccount = new Account();
        expectedAccount.setAccountHolder(accountHolder);
        expectedAccount.setAccountType(accountType);
        expectedAccount.setCurrency(currency);
        expectedAccount.setBalance(initialBalance);

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account savedAccount = invocation.getArgument(0);
            expectedAccount.setAccountNumber(savedAccount.getAccountNumber());
            return expectedAccount;
        });

        // Act
        Account createdAccount = accountService.createAccount(accountHolder, accountType, currency, initialBalance);

        // Assert
        assertNotNull(createdAccount);
        assertEquals(accountHolder, createdAccount.getAccountHolder());
        assertEquals(accountType, createdAccount.getAccountType());
        assertEquals(currency, createdAccount.getCurrency());
        assertEquals(initialBalance, createdAccount.getBalance());
        assertNotNull(createdAccount.getAccountNumber());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void createAccount_WithNullInitialBalance_ShouldSetZeroBalance() {
        // Arrange
        String accountHolder = "Jane Doe";
        String accountType = "CHECKING";
        String currency = "EUR";

        Account expectedAccount = new Account();
        expectedAccount.setAccountHolder(accountHolder);
        expectedAccount.setAccountType(accountType);
        expectedAccount.setCurrency(currency);
        expectedAccount.setBalance(BigDecimal.ZERO);

        when(accountRepository.save(any(Account.class))).thenReturn(expectedAccount);

        // Act
        Account createdAccount = accountService.createAccount(accountHolder, accountType, currency, null);

        // Assert
        assertNotNull(createdAccount);
        assertEquals(BigDecimal.ZERO, createdAccount.getBalance());
        verify(accountRepository, times(1)).save(any(Account.class));
    }
}