package com.learn.rate_limiter.dto;

public class RateLimitErrorResponse {
    private String error;
    private long retryAfterSeconds;

    public RateLimitErrorResponse(String error, long retryAfterSeconds) {
        this.error = error;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getError() {
        return error;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}