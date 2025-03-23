package com.example.easybank.aspect;

import com.example.easybank.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {
    private final RateLimiterService rateLimiterService;

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        
        String clientIp = request.getRemoteAddr();
        String endpoint = request.getRequestURI();
        String key = clientIp + ":" + endpoint;

        // This will throw TooManyRequestsException if rate limit is exceeded
        rateLimiterService.checkRateLimit(key);
        
        return joinPoint.proceed();
    }
} 