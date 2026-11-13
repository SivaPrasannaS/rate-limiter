package com.learn.rate_limiter.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Component
public class FixedWindowRateLimitFilter extends OncePerRequestFilter {
    
    private static final int MAX_REQUESTS = 5;         // limit
    private static final long WINDOW_MILLS = 60_000;   // 1 minute window

    // clientKey → that client's current window
    private final Map<String, Window> clients = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public FixedWindowRateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = request.getRemoteAddr();
        long now = System.currentTimeMillis();

        // Atomically update this client's counter
        Window window = clients.compute(clientKey, (key, existing) -> {
            if(existing == null || now - existing.startMills() >= WINDOW_MILLS) {
                return new Window(now, 1);                              // new windos starts
            }
            return new Window(existing.startMills(), existing.count() + 1);   // same window
        });

        int remaining = Math.max(0, MAX_REQUESTS - window.count());
        response.setHeader("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        if(window.count() > MAX_REQUESTS) {
            long remainingMilliSeconds = window.startMills() + WINDOW_MILLS - now;
            long retryAfterSeconds = (remainingMilliSeconds + 999) / 1000;

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON.toString());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Too many requests");
            errorResponse.put("retryAfterSeconds", retryAfterSeconds);

            objectMapper.writeValue(response.getWriter(), errorResponse);
            return;
        }

        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Only rate-limit API endpoints
        return !request.getRequestURI().startsWith("/api/");
    }
    
    private record Window(long startMills, int count) {}

}