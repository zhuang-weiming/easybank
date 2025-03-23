package com.example.easybank.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Aspect
@Component
@Slf4j
public class PerformanceMonitoringAspect {

    private final MeterRegistry meterRegistry;

    public PerformanceMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Around("execution(* com.example.easybank.service.TransactionService.processTransaction(..))")
    public Object measureTransactionPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String fullMethodName = className + "." + methodName;
        
        Object[] arguments = joinPoint.getArgs();
        String sourceAccount = arguments.length > 0 ? (String) arguments[0] : "unknown";
        String destinationAccount = arguments.length > 1 ? (String) arguments[1] : "unknown";
        
        log.info("Starting transaction processing: {} -> {}", sourceAccount, destinationAccount);
        
        stopWatch.start();
        Object result = null;
        boolean success = false;
        
        try {
            result = joinPoint.proceed();
            success = true;
            return result;
        } catch (Throwable e) {
            log.error("Transaction processing failed: {}", e.getMessage());
            throw e;
        } finally {
            stopWatch.stop();
            long executionTime = stopWatch.getTotalTimeMillis();
            
            log.info("Transaction processing completed in {}ms: {} -> {} (success={})", 
                     executionTime, sourceAccount, destinationAccount, success);
            
            // Record metrics using Micrometer
            Timer.builder("transaction.processing.time")
                 .tag("method", fullMethodName)
                 .tag("success", String.valueOf(success))
                 .tag("source", anonymizeAccount(sourceAccount))
                 .register(meterRegistry)
                 .record(executionTime, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
    
    @Around("execution(* com.example.easybank.repository.AccountRepository.*(..)) || " +
            "execution(* com.example.easybank.repository.TransactionRepository.*(..))")
    public Object measureRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String fullMethodName = className + "." + methodName;
        
        stopWatch.start();
        
        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            long executionTime = stopWatch.getTotalTimeMillis();
            
            if (executionTime > 50) {  // Only log slow queries
                log.warn("Slow database operation: {}ms for {}", executionTime, fullMethodName);
            }
            
            // Record repository metrics
            Timer.builder("database.operation.time")
                 .tag("operation", fullMethodName)
                 .register(meterRegistry)
                 .record(executionTime, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
    
    // Anonymize account number for privacy in metrics
    private String anonymizeAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "unknown";
        }
        return accountNumber.substring(0, 2) + "***";
    }
} 