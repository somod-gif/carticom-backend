package com.carticom.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit coverage of the token bucket bookkeeping - no Spring context needed.
 *
 * <p>The default tier is reconfigured to a tiny burst (2) with a fast refill
 * (600 tokens/minute == one token every 100ms) so the refill behaviour can be
 * asserted without waiting for a production-length window.
 */
class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.getTiers().put("default", new RateLimitProperties.Tier(2, 600, 1));
        service = new RateLimitService(properties);
    }

    @Test
    void allowsUpToCapacityThenRejectsTheNextCallWithRetryAfter() {
        String key = "seller-1";

        assertTrue(service.tryConsume(RateLimitTier.DEFAULT, key).allowed(), "1st call must pass");
        assertTrue(service.tryConsume(RateLimitTier.DEFAULT, key).allowed(), "2nd call must pass");

        RateLimitResult rejected = service.tryConsume(RateLimitTier.DEFAULT, key);
        assertFalse(rejected.allowed(), "the call after capacity must be rejected");
        assertTrue(rejected.retryAfterSeconds() > 0,
                "a rejection must carry a positive retryAfterSeconds");
    }

    @Test
    void recoversAfterRefill() throws InterruptedException {
        String key = "seller-2";
        service.tryConsume(RateLimitTier.DEFAULT, key);
        service.tryConsume(RateLimitTier.DEFAULT, key);
        assertFalse(service.tryConsume(RateLimitTier.DEFAULT, key).allowed());

        Thread.sleep(250); // 600 tokens/minute == one token per 100ms

        assertTrue(service.tryConsume(RateLimitTier.DEFAULT, key).allowed(),
                "the bucket must hand tokens back over time");
    }

    @Test
    void keepsASeparateBucketPerKey() {
        String exhausted = "seller-3";
        service.tryConsume(RateLimitTier.DEFAULT, exhausted);
        service.tryConsume(RateLimitTier.DEFAULT, exhausted);
        assertFalse(service.tryConsume(RateLimitTier.DEFAULT, exhausted).allowed());

        assertTrue(service.tryConsume(RateLimitTier.DEFAULT, "seller-4").allowed(),
                "one caller hitting the limit must not block anybody else");
        assertTrue(service.getTrackedBucketCount() >= 2, "each key gets its own bucket");
    }
}
