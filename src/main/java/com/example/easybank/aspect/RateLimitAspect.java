package com.example.easybank.aspect;

import com.example.easybank.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

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
        String pattern = getRequestPattern(joinPoint);
        String key = clientIp + ":" + pattern;

        // This will throw TooManyRequestsException if rate limit is exceeded
        rateLimiterService.checkRateLimit(key);
        
        return joinPoint.proceed();
    }

    private String getRequestPattern(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // Get the class-level RequestMapping if it exists
        RequestMapping classMapping = method.getDeclaringClass().getAnnotation(RequestMapping.class);
        String basePattern = "";
        if (classMapping != null && classMapping.value().length > 0) {
            basePattern = classMapping.value()[0];
        }
        
        // Get the method-level mapping
        String methodPattern = "";
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            methodPattern = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            methodPattern = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            methodPattern = mapping.value().length > 0 ? mapping.value()[0] : "";
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            methodPattern = mapping.value().length > 0 ? mapping.value()[0] : "";
        }
        
        // Normalize the pattern by removing trailing slashes
        String pattern = basePattern + methodPattern;
        while (pattern.endsWith("/")) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }
        
        return pattern;
    }
} 