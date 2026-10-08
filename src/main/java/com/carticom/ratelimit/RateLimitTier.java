package com.carticom.ratelimit;

/**
 * The rate-limit buckets exposed by the API. Each tier carries its production
 * defaults (burst capacity, refill tokens and refill window) and the identity
 * the bucket is keyed on. Defaults can be overridden per deployment under
 * {@code ratelimit.tiers.<name>} in application.yml.
 */
public enum RateLimitTier {

    /**
     * Credential endpoints (login, register, password reset, invite accept) -
     * brute force protection keyed by client IP.
     */
    AUTH(10, 10, 1, KeyScope.IP),

    /**
     * Image and store logo/banner uploads - keyed by the signed-in seller.
     */
    UPLOAD(30, 30, 1, KeyScope.USER),

    /**
     * Paid AI calls - 20 requests, refilled one token every ~72 minutes (about 20/day),
     * keyed by the signed-in user.
     */
    AI(20, 1, 72, KeyScope.USER),

    /**
     * Public storefront / buyer / cart browsing - keyed by client IP so anonymous
     * traffic is still capped.
     */
    PUBLIC(300, 300, 1, KeyScope.IP),

    /**
     * Any other mutating request - keyed by the signed-in user.
     */
    WRITE(60, 60, 1, KeyScope.USER),

    /**
     * Fallback for every request that matched nothing above.
     */
    DEFAULT(600, 600, 1, KeyScope.USER);

    private final RateLimitProperties.Tier defaults;
    private final KeyScope keyScope;

    RateLimitTier(long capacity, long refillTokens, long refillMinutes, KeyScope keyScope) {
        this.defaults = new RateLimitProperties.Tier(capacity, refillTokens, refillMinutes);
        this.keyScope = keyScope;
    }

    /**
     * Built-in bucket shape for this tier, used for any field the deployment did not override.
     */
    public RateLimitProperties.Tier getDefaults() {
        return defaults;
    }

    public KeyScope getKeyScope() {
        return keyScope;
    }

    /**
     * Which identity a tier is metered on. {@link KeyScope#USER} falls back to the
     * client IP for anonymous traffic so unauthenticated callers get their own bucket
     * instead of sharing one.
     */
    public enum KeyScope {
        USER,
        IP
    }
}
