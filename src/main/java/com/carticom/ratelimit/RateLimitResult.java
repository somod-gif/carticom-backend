package com.carticom.ratelimit;

/**
 * Outcome of a single rate-limit check.
 *
 * @param allowed          whether the request may proceed
 * @param retryAfterSeconds seconds the caller should wait before retrying; always 0 when allowed
 */
public record RateLimitResult(boolean allowed, long retryAfterSeconds) {

    public static RateLimitResult pass() {
        return new RateLimitResult(true, 0);
    }

    public static RateLimitResult fail(long retryAfterSeconds) {
        return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
    }
}
