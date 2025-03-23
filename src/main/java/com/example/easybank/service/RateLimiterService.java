package com.example.easybank.service;

import com.example.easybank.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimiterService {

    @Value("${app.rate-limit.max-requests-per-minute:600}")
    private int maxRequestsPerMinute;
    
    @Value("${app.rate-limit.max-transactions-per-minute:100}")
    private int maxTransactionsPerMinute;
    
    @Value("${app.rate-limit.retry-after-seconds:30}")
    private int retryAfterSeconds;
    
    private static final long WINDOW_SIZE_MS = 60000; // 1 minute in milliseconds

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Checks if the request should be allowed based on rate limits
     * @param key The identifier for the request (usually IP or user ID)
     * @throws TooManyRequestsException if the rate limit is exceeded
     */
    public void checkRateLimit(String key) {
        String requestKey = "ratelimit:" + key;
        long currentTime = System.currentTimeMillis();
        
        try {
            // Remove old requests outside the window
            redisTemplate.opsForZSet().removeRangeByScore(requestKey, 0, currentTime - WINDOW_SIZE_MS);
            
            // Get the current count after removing expired entries
            Long currentCount = redisTemplate.opsForZSet().size(requestKey);
            if (currentCount == null) {
                currentCount = 0L;
            }
            
            log.debug("Current request count for key {}: {}", key, currentCount);
            
            // Check if we've already reached or exceeded the limit
            if (currentCount >= maxRequestsPerMinute) {
                log.debug("Rate limit exceeded for key {}: {} requests", key, currentCount);
                throw new TooManyRequestsException(
                    "Rate limit exceeded. Please try again later.",
                    retryAfterSeconds
                );
            }
            
            // If we're within limits, add the current request
            String requestId = currentTime + ":" + Thread.currentThread().getId();
            redisTemplate.opsForZSet().add(requestKey, requestId, currentTime);
            
            // Set expiry on the key to clean up eventually
            redisTemplate.expire(requestKey, 2, TimeUnit.MINUTES);
            
            // Check again after adding the new request
            currentCount = redisTemplate.opsForZSet().size(requestKey);
            if (currentCount == null) {
                currentCount = 0L;
            }
            
            // Now make sure we didn't exceed the limit with this request
            if (currentCount > maxRequestsPerMinute) {
                log.debug("Rate limit exceeded for key {}: {} requests (post-check)", key, currentCount);
                // Remove the request we just added
                redisTemplate.opsForZSet().remove(requestKey, requestId);
                throw new TooManyRequestsException(
                    "Rate limit exceeded. Please try again later.",
                    retryAfterSeconds
                );
            }
            
        } catch (TooManyRequestsException e) {
            throw e; // Re-throw our custom exception
        } catch (Exception e) {
            log.error("Error checking rate limit for key: " + key, e);
            throw new TooManyRequestsException(
                "Error processing request. Please try again later.",
                retryAfterSeconds
            );
        }
    }

    /**
     * Checks if a transaction should be allowed based on account-specific rate limits
     * @param accountId The account identifier
     * @throws TooManyRequestsException if the rate limit is exceeded
     */
    public void checkTransactionRateLimit(String accountId) {
        String transactionKey = "ratelimit:transaction:" + accountId;
        long currentTime = System.currentTimeMillis();
        
        try {
            // Remove old transactions outside the window
            redisTemplate.opsForZSet().removeRangeByScore(transactionKey, 0, currentTime - WINDOW_SIZE_MS);
            
            // Get the current count after removing expired entries
            Long currentCount = redisTemplate.opsForZSet().size(transactionKey);
            if (currentCount == null) {
                currentCount = 0L;
            }
            
            log.debug("Current transaction count for account {}: {}", accountId, currentCount);
            
            // Check if we've already reached or exceeded the limit
            if (currentCount >= maxTransactionsPerMinute) {
                log.debug("Transaction rate limit exceeded for account {}: {} transactions", accountId, currentCount);
                throw new TooManyRequestsException(
                    "Transaction rate limit exceeded for this account. Please try again later.",
                    retryAfterSeconds
                );
            }
            
            // If we're within limits, add the current transaction
            String transactionId = currentTime + ":" + Thread.currentThread().getId();
            redisTemplate.opsForZSet().add(transactionKey, transactionId, currentTime);
            
            // Set expiry on the key to clean up eventually
            redisTemplate.expire(transactionKey, 2, TimeUnit.MINUTES);
            
            // Check again after adding the new transaction
            currentCount = redisTemplate.opsForZSet().size(transactionKey);
            if (currentCount == null) {
                currentCount = 0L;
            }
            
            // Now make sure we didn't exceed the limit with this transaction
            if (currentCount > maxTransactionsPerMinute) {
                log.debug("Transaction rate limit exceeded for account {}: {} transactions (post-check)", accountId, currentCount);
                // Remove the transaction we just added
                redisTemplate.opsForZSet().remove(transactionKey, transactionId);
                throw new TooManyRequestsException(
                    "Transaction rate limit exceeded for this account. Please try again later.",
                    retryAfterSeconds
                );
            }
            
        } catch (TooManyRequestsException e) {
            throw e; // Re-throw our custom exception
        } catch (Exception e) {
            log.error("Error checking transaction rate limit for account: " + accountId, e);
            throw new TooManyRequestsException(
                "Error processing transaction. Please try again later.",
                retryAfterSeconds
            );
        }
    }
} 