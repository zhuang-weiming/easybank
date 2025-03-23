package com.example.easybank.exception;

public class TooManyRequestsException extends RuntimeException {
    
    private final int retryAfterSeconds;
    
    public TooManyRequestsException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
} 