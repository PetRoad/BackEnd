package com.petroad.backend.security;

public class LoginRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitException(long retryAfterSeconds) {
        super("로그인 요청이 너무 많습니다. 잠시 후 다시 시도하세요.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
