package com.example.easybank.service;

import com.example.easybank.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    
    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        // Set up ZSet operations
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        
        // Set the rate limit values via reflection
        ReflectionTestUtils.setField(rateLimiterService, "maxRequestsPerMinute", 600);
        ReflectionTestUtils.setField(rateLimiterService, "maxTransactionsPerMinute", 100);
        ReflectionTestUtils.setField(rateLimiterService, "retryAfterSeconds", 30);
    }

    @Test
    void testBasicRateLimit() {
        // Setup
        String key = "test-key";
        
        // First, setup redis to return count under the limit
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.size(anyString())).thenReturn(599L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // Test under limit
        assertDoesNotThrow(() -> rateLimiterService.checkRateLimit(key), 
            "Request under limit should be allowed");
        
        // Setup for at limit
        when(zSetOperations.size(anyString())).thenReturn(600L);
        
        // Test at limit (next call should throw exception)
        assertThrows(TooManyRequestsException.class, 
            () -> rateLimiterService.checkRateLimit(key),
            "Request at limit should throw TooManyRequestsException");
        
        // Verify the retry after header value
        try {
            rateLimiterService.checkRateLimit(key);
            fail("Should have thrown TooManyRequestsException");
        } catch (TooManyRequestsException e) {
            assertEquals(30, e.getRetryAfterSeconds(), "Should have correct retry after value");
        }
    }

    @Test
    void testTransactionRateLimit() {
        String accountNumber = "test-account";
        
        // Setup for under limit
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.size(anyString())).thenReturn(99L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // Test under limit
        assertDoesNotThrow(() -> rateLimiterService.checkTransactionRateLimit(accountNumber), 
            "Transaction under limit should be allowed");
        
        // Setup for at limit
        when(zSetOperations.size(anyString())).thenReturn(100L);
        
        // Test at limit (next call should throw exception)
        assertThrows(TooManyRequestsException.class, 
            () -> rateLimiterService.checkTransactionRateLimit(accountNumber),
            "Transaction at limit should throw TooManyRequestsException");
        
        // Verify exception message contains account info
        try {
            rateLimiterService.checkTransactionRateLimit(accountNumber);
            fail("Should have thrown TooManyRequestsException");
        } catch (TooManyRequestsException e) {
            assertTrue(e.getMessage().contains("account"), 
                "Exception message should mention account");
        }
    }

    @Test
    void testPostCheckRateLimit() {
        String key = "test-key";
        
        // Setup the first size check to be under limit
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.size(anyString())).thenReturn(599L).thenReturn(601L);  // Under limit first, over limit after add
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // Test that it throws an exception after the post-check
        assertThrows(TooManyRequestsException.class, 
            () -> rateLimiterService.checkRateLimit(key),
            "Should throw exception on post-check");
    }

    @Test
    void testPostCheckTransactionRateLimit() {
        String accountNumber = "test-account";
        
        // Setup the first size check to be under limit
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(zSetOperations.size(anyString())).thenReturn(99L).thenReturn(101L);  // Under limit first, over limit after add
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        // Test that it throws an exception after the post-check
        assertThrows(TooManyRequestsException.class, 
            () -> rateLimiterService.checkTransactionRateLimit(accountNumber),
            "Should throw exception on post-check");
    }
} 