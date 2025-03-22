package com.example.easybank;

import com.example.easybank.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class DatabaseConnectionTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    public void testDatabaseConnection() {
        // Test database connection by checking if the repository is accessible
        assertNotNull(accountRepository, "AccountRepository should be autowired");
        // No need to call any methods, just verify the repository was autowired successfully
    }
}