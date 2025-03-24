package com.example.easybank.integration;

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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // This ensures all test data is rolled back after the test
public class AccountBusinessFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;
    
    @Autowired
    private TransactionService transactionService;

    /**
     * Test the complete business flow of the 4 main APIs:
     * 1. POST /api/accounts - Create two accounts
     * 2. GET /api/accounts/{accountNumber} - Get account details
     * 3. POST /api/accounts/{sourceAccountNumber}/transfer - Transfer money
     * 4. GET /api/accounts/{accountNumber}/transactions - Get transactions
     */
    @Test
    public void testCompleteBusinessFlow() throws Exception {
        // Step 1: Create first account with $1000
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
                .andExpect(jsonPath("$.accountNumber").exists())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn();
                
        String firstAccountResponse = firstAccountResult.getResponse().getContentAsString();
        String firstAccountNumber = extractAccountNumber(firstAccountResponse);
        
        // Create second account with $500
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
                .andExpect(jsonPath("$.accountNumber").exists())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andReturn();
                
        String secondAccountResponse = secondAccountResult.getResponse().getContentAsString();
        String secondAccountNumber = extractAccountNumber(secondAccountResponse);
        
        // Step 2: Verify both accounts were created correctly using GET endpoint
        mockMvc.perform(get("/api/accounts/{accountNumber}", firstAccountNumber)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolder", is("John Doe")))
                .andExpect(jsonPath("$.accountType", is("SAVINGS")))
                .andExpect(jsonPath("$.balance", is(1000.00)))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
                
        mockMvc.perform(get("/api/accounts/{accountNumber}", secondAccountNumber)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountHolder", is("Jane Smith")))
                .andExpect(jsonPath("$.accountType", is("CHECKING")))
                .andExpect(jsonPath("$.balance", is(500.00)))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
        
        // Step 3: Transfer $300 from first account to second account
        mockMvc.perform(post("/api/accounts/{sourceAccountNumber}/transfer", firstAccountNumber)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("destinationAccountNumber", secondAccountNumber)
                .param("amount", "300.00"))
                .andExpect(status().isOk());
        
        // Step 4: Verify account balances after transfer
        mockMvc.perform(get("/api/accounts/{accountNumber}", firstAccountNumber)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(700.00)));
        
        mockMvc.perform(get("/api/accounts/{accountNumber}", secondAccountNumber)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(800.00)));
        
        // Step 5: Get transaction history for both accounts
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions", firstAccountNumber)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount", is(-300.00)))
                .andExpect(jsonPath("$[0].transactionType", is("TRANSFER")));
        
        mockMvc.perform(get("/api/accounts/{accountNumber}/transactions", secondAccountNumber)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount", is(300.00)))
                .andExpect(jsonPath("$[0].transactionType", is("TRANSFER")));
    }
    
    private String extractAccountNumber(String accountJson) {
        // Find the accountNumber field
        String accountNumberField = "\"accountNumber\":\"";
        int start = accountJson.indexOf(accountNumberField);
        if (start == -1) {
            throw new IllegalArgumentException("Account number not found in response: " + accountJson);
        }
        start += accountNumberField.length();
        int end = accountJson.indexOf("\"", start);
        if (end == -1) {
            throw new IllegalArgumentException("Invalid JSON format for account number: " + accountJson);
        }
        String accountNumber = accountJson.substring(start, end);
        if (accountNumber.isEmpty()) {
            throw new IllegalArgumentException("Empty account number in response: " + accountJson);
        }
        return accountNumber;
    }
} 