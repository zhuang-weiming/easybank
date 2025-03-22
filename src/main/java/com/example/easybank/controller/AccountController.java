package com.example.easybank.controller;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.service.AccountService;
import com.example.easybank.service.TransactionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@Api(tags = "Account Management", description = "APIs for managing bank accounts and transactions")  
public class AccountController {
    private final TransactionService transactionService;
    private final AccountService accountService;

    public AccountController(TransactionService transactionService, AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
    }
    
    @ApiOperation(value = "Get account details", notes = "Retrieves account information by account number")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<Account> getAccount(
            @ApiParam(value = "Account number", required = true)
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.getAccount(accountNumber));
    }
    
    @ApiOperation(value = "Transfer money", notes = "Transfer money between two accounts")
    @PostMapping("/{sourceAccountNumber}/transfer")
    public ResponseEntity<Transaction> transfer(
            @ApiParam(value = "Source account number", required = true)
            @PathVariable String sourceAccountNumber,
            @ApiParam(value = "Destination account number", required = true)
            @RequestParam String destinationAccountNumber,
            @ApiParam(value = "Amount to transfer", required = true, example = "100.00")
            @RequestParam BigDecimal amount) {
        Transaction transaction = transactionService.processTransaction(
                sourceAccountNumber,
                destinationAccountNumber,
                amount
        );
        return ResponseEntity.ok(transaction);
    }
    
    @ApiOperation(value = "Get account transactions", notes = "Retrieves all transactions for a specific account")
    @GetMapping("/{accountNumber}/transactions")
    public ResponseEntity<List<Transaction>> getAccountTransactions(
            @ApiParam(value = "Account number", required = true)
            @PathVariable String accountNumber) {
        return ResponseEntity.ok(transactionService.getAccountTransactions(accountNumber));
    }

    @ApiOperation(value = "Create new account", notes = "Creates a new bank account with the specified details")
    @PostMapping
    public ResponseEntity<Account> createAccount(
            @ApiParam(value = "Account holder name", required = true)
            @RequestParam String accountHolder,
            @ApiParam(value = "Account type (e.g. SAVINGS, CHECKING)", required = true)
            @RequestParam String accountType,
            @ApiParam(value = "Currency code (e.g. USD)", required = false)
            @RequestParam(required = false, defaultValue = "USD") String currency,
            @ApiParam(value = "Initial balance", required = false, example = "0.00")
            @RequestParam(required = false) BigDecimal initialBalance) {
        return ResponseEntity.ok(accountService.createAccount(accountHolder, accountType, currency, initialBalance));
    }
}
