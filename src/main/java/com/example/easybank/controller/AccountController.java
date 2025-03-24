package com.example.easybank.controller;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.dto.TransactionResponse;
import com.example.easybank.service.AccountService;
import com.example.easybank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Tag(name = "Account Management", description = "APIs for managing bank accounts and transactions")
public class AccountController {
    private final TransactionService transactionService;
    private final AccountService accountService;

    public AccountController(TransactionService transactionService, AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
    }
    
    @Operation(summary = "Get account details", description = "Retrieves account information by account number")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<Account> getAccount(
            @Parameter(description = "Account number", required = true)
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }
    
    @Operation(summary = "Create new account", description = "Creates a new bank account")
    @PostMapping
    public ResponseEntity<Account> createAccount(
            @Parameter(description = "Account holder name", required = true)
            @RequestParam String accountHolder,
            @Parameter(description = "Account type (SAVINGS/CHECKING)", required = true)
            @RequestParam String accountType,
            @Parameter(description = "Currency code (e.g., USD)", required = true)
            @RequestParam String currency,
            @Parameter(description = "Initial balance", required = true)
            @RequestParam BigDecimal initialBalance) {
        return ResponseEntity.ok(accountService.createAccount(accountHolder, accountType, currency, initialBalance));
    }
    
    @Operation(summary = "Transfer money", description = "Transfer money between two accounts")
    @PostMapping("/{sourceAccountNumber}/transfer")
    public ResponseEntity<Transaction> transfer(
            @Parameter(description = "Source account number", required = true)
            @PathVariable String sourceAccountNumber,
            @Parameter(description = "Destination account number", required = true)
            @RequestParam String destinationAccountNumber,
            @Parameter(description = "Amount to transfer", required = true, example = "100.00")
            @RequestParam BigDecimal amount) {
        Transaction transaction = transactionService.processTransaction(
                sourceAccountNumber,
                destinationAccountNumber,
                amount
        );
        return ResponseEntity.ok(transaction);
    }
    
    @Operation(summary = "Get account transactions", description = "Retrieves all transactions for an account")
    @GetMapping("/{accountNumber}/transactions")
    public ResponseEntity<List<TransactionResponse>> getAccountTransactions(
            @Parameter(description = "Account number", required = true)
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.getAccountTransactions(accountNumber));
    }
}
