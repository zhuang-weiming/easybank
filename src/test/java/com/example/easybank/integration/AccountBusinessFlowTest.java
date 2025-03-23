package com.example.easybank.integration;

import com.example.easybank.domain.Account;
import com.example.easybank.domain.Transaction;
import com.example.easybank.service.AccountService;
import com.example.easybank.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AccountBusinessFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;

    /**
     * Test the complete business flow:
     * 1. Create two accounts
     * 2. Get account details for verification
     * 3. Transfer money between accounts
     * 4. Get account transactions to confirm transfer
     * 5. Verify final account details
     */
    @Test
    public void testCompleteBusinessFlow() throws Exception {
        // Step 1: Create first account
        MvcResult firstAccountResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("accountHolder", "John Doe")
                .param("accountType", "SAVINGS")
                .param("currency", "USD")
                .param("initialBalance", "1000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolder", is("John Doe")))
                .andExpect(jsonPath("$.accountType", is("SAVINGS")))
                .andExpect(jsonPath("$.balance", is(1000.00)))
                .andReturn();
                
        String firstAccountResponse = firstAccountResult.getResponse().getContentAsString();
        String firstAccountNumber = extractAccountNumber(firstAccountResponse);
        
        // Create second account
        MvcResult secondAccountResult = mockMvc.perform(post("/api/accounts")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("accountHolder", "Jane Smith")
                .param("accountType", "CHECKING")
                .param("currency", "USD")
                .param("initialBalance", "500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolder", is("Jane Smith")))
                .andExpect(jsonPath("$.accountType", is("CHECKING")))
                .andExpect(jsonPath("$.balance", is(500.00)))
                .andReturn();
                
        String secondAccountResponse = secondAccountResult.getResponse().getContentAsString();
        String secondAccountNumber = extractAccountNumber(secondAccountResponse);
        
        // Step 2: Get account details for verification
        mockMvc.perform(get("/api/accounts/{accountNumber}", firstAccountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber", is(firstAccountNumber)))
                .andExpect(jsonPath("$.accountHolder", is("John Doe")))
                .andExpect(jsonPath("$.balance", is(1000.00)));
                
        mockMvc.perform(get("/api/accounts/{accountNumber}", secondAccountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber", is(secondAccountNumber)))
                .andExpect(jsonPath("$.accountHolder", is("Jane Smith")))
                .andExpect(jsonPath("$.balance", is(500.00)));
        
        // Step 3: Transfer money between accounts
        BigDecimal transferAmount = new BigDecimal("250.00");
        mockMvc.perform(post("/api/accounts/{sourceAccountNumber}/transfer", firstAccountNumber)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("destinationAccountNumber", secondAccountNumber)
                .param("amount", transferAmount.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(250.00)))
                .andExpect(jsonPath("$.sourceAccount.accountNumber", is(firstAccountNumber)))
                .andExpect(jsonPath("$.destinationAccount.accountNumber", is(secondAccountNumber)));
        
        // Step 4: Get account transactions to confirm transfer
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions", firstAccountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].amount", is(250.00)))
                .andExpect(jsonPath("$[0].sourceAccount.accountNumber", is(firstAccountNumber)))
                .andExpect(jsonPath("$[0].destinationAccount.accountNumber", is(secondAccountNumber)));
        
        // Also check second account transactions
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions", secondAccountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].amount", is(250.00)))
                .andExpect(jsonPath("$[0].sourceAccount.accountNumber", is(firstAccountNumber)))
                .andExpect(jsonPath("$[0].destinationAccount.accountNumber", is(secondAccountNumber)));
        
        // Step 5: Verify final account details after transfer
        mockMvc.perform(get("/api/accounts/{accountNumber}", firstAccountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber", is(firstAccountNumber)))
                .andExpect(jsonPath("$.accountHolder", is("John Doe")))
                .andExpect(jsonPath("$.balance", is(750.00))); // 1000 - 250
                
        mockMvc.perform(get("/api/accounts/{accountNumber}", secondAccountNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber", is(secondAccountNumber)))
                .andExpect(jsonPath("$.accountHolder", is("Jane Smith")))
                .andExpect(jsonPath("$.balance", is(750.00))); // 500 + 250
    }
    
    // Direct test of service layer using the same business flow
    @Test
    public void testBusinessFlowWithServiceLayer() {
        // Step 1: Create accounts
        Account firstAccount = accountService.createAccount(
            "John Doe", "SAVINGS", "USD", new BigDecimal("1000.00"));
        Account secondAccount = accountService.createAccount(
            "Jane Smith", "CHECKING", "USD", new BigDecimal("500.00"));
            
        assertNotNull(firstAccount.getAccountNumber());
        assertNotNull(secondAccount.getAccountNumber());
        assertEquals("John Doe", firstAccount.getAccountHolder());
        assertEquals("Jane Smith", secondAccount.getAccountHolder());
        
        // Step 2: Get account details for verification
        Account retrievedFirstAccount = transactionService.getAccount(firstAccount.getAccountNumber());
        Account retrievedSecondAccount = transactionService.getAccount(secondAccount.getAccountNumber());
        
        assertEquals(firstAccount.getAccountNumber(), retrievedFirstAccount.getAccountNumber());
        assertEquals(secondAccount.getAccountNumber(), retrievedSecondAccount.getAccountNumber());
        assertEquals(new BigDecimal("1000.00"), retrievedFirstAccount.getBalance());
        assertEquals(new BigDecimal("500.00"), retrievedSecondAccount.getBalance());
        
        // Step 3: Transfer money between accounts
        BigDecimal transferAmount = new BigDecimal("250.00");
        Transaction transaction = transactionService.processTransaction(
            firstAccount.getAccountNumber(), 
            secondAccount.getAccountNumber(),
            transferAmount
        );
        
        assertNotNull(transaction);
        assertEquals(transferAmount, transaction.getAmount());
        
        // Step 4: Get account transactions to confirm transfer
        List<Transaction> firstAccountTransactions = 
            transactionService.getAccountTransactions(firstAccount.getAccountNumber());
        List<Transaction> secondAccountTransactions = 
            transactionService.getAccountTransactions(secondAccount.getAccountNumber());
        
        assertFalse(firstAccountTransactions.isEmpty());
        assertFalse(secondAccountTransactions.isEmpty());
        assertEquals(transferAmount, firstAccountTransactions.get(0).getAmount());
        
        // Step 5: Verify final account details after transfer
        Account updatedFirstAccount = transactionService.getAccount(firstAccount.getAccountNumber());
        Account updatedSecondAccount = transactionService.getAccount(secondAccount.getAccountNumber());
        
        assertEquals(new BigDecimal("750.00"), updatedFirstAccount.getBalance()); // 1000 - 250
        assertEquals(new BigDecimal("750.00"), updatedSecondAccount.getBalance()); // 500 + 250
        
        // Make sure account holder names are preserved
        assertEquals("John Doe", updatedFirstAccount.getAccountHolder());
        assertEquals("Jane Smith", updatedSecondAccount.getAccountHolder());
    }
    
    private String extractAccountNumber(String accountJson) {
        // Simple extraction - in a real app you might want to use a JSON parser
        int start = accountJson.indexOf("\"accountNumber\":\"") + 17;
        int end = accountJson.indexOf("\"", start);
        return accountJson.substring(start, end);
    }
} 