package com.carticom.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the tier table, the exempt paths and the 429 contract of the filter.
 */
class RateLimitFilterTest {

    private RateLimitProperties properties;
    private RateLimitService service;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        service = new RateLimitService(properties);
        filter = new RateLimitFilter(properties, service, new RateKeyResolver());
    }

    @Test
    void mapsEveryRequestToItsTier() {
        record Case(String method, String path, RateLimitTier expected) {}
        Case[] cases = {
                // AUTH
                new Case("POST", "/api/v1/auth/login", RateLimitTier.AUTH),
                new Case("POST", "/api/v1/auth/register", RateLimitTier.AUTH),
                new Case("POST", "/api/v1/auth/forgot-password", RateLimitTier.AUTH),
                new Case("POST", "/api/v1/auth/reset-password", RateLimitTier.AUTH),
                new Case("POST", "/api/v1/invites/abc-123/accept", RateLimitTier.AUTH),
                // UPLOAD
                new Case("POST", "/api/v1/images/upload", RateLimitTier.UPLOAD),
                new Case("POST", "/api/v1/storage/upload", RateLimitTier.UPLOAD),
                new Case("POST", "/api/v1/stores/7/logo", RateLimitTier.UPLOAD),
                new Case("POST", "/api/v1/stores/7/banner", RateLimitTier.UPLOAD),
                // AI
                new Case("POST", "/api/v1/ai/chat", RateLimitTier.AI),
                new Case("POST", "/api/v1/ai/generate-description", RateLimitTier.AI),
                new Case("GET", "/api/v1/ai/insights", RateLimitTier.AI),
                // PUBLIC
                new Case("GET", "/api/v1/storefront/stores/acme", RateLimitTier.PUBLIC),
                new Case("GET", "/api/v1/buyer/orders", RateLimitTier.PUBLIC),
                new Case("POST", "/api/v1/cart/items", RateLimitTier.PUBLIC),
                new Case("POST", "/api/v1/checkout/session", RateLimitTier.PUBLIC),
                new Case("GET", "/api/v1/images/file/logo.png", RateLimitTier.PUBLIC),
                new Case("GET", "/api/v1/orders/track/42", RateLimitTier.PUBLIC),
                // WRITE
                new Case("POST", "/api/v1/products", RateLimitTier.WRITE),
                new Case("PUT", "/api/v1/stores/7/branding", RateLimitTier.WRITE),
                new Case("DELETE", "/api/v1/stores/7", RateLimitTier.WRITE),
                // DEFAULT - reads and paths that belong to no tier
                new Case("GET", "/api/v1/products", RateLimitTier.DEFAULT),
                new Case("GET", "/api/v1/stores/7", RateLimitTier.DEFAULT),
                new Case("GET", "/api/v1/ai/status/7", RateLimitTier.DEFAULT),
        };

        for (Case testCase : cases) {
            assertEquals(testCase.expected(), filter.resolveTier(testCase.method(), testCase.path()),
                    testCase.method() + " " + testCase.path());
        }
    }

    @Test
    void exemptPathsAreNeverMetered() {
        assertTrue(filter.isExempt("/api/v1/payments/webhook/paystack"));
        assertTrue(filter.isExempt("/api/v1/waitlist/join"));
        assertTrue(filter.isExempt("/actuator/health"));
        assertTrue(filter.isExempt("/swagger-ui/index.html"));
        assertTrue(filter.isExempt("/v3/api-docs/swagger-config"));
        assertFalse(filter.isExempt("/api/v1/auth/login"));
        assertFalse(filter.isExempt("/api/v1/storefront/stores/acme"));
    }

    @Test
    void passesExemptRequestsThroughWithoutChargingThem() throws Exception {
        for (int i = 0; i < 50; i++) {
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                    new MockHttpServletResponse(), chain);
            assertNotNull(chain.getRequest(), "exempt requests must always reach the chain");
        }
        assertEquals(0, service.getTrackedBucketCount(), "exempt paths must not create buckets");
    }

    @Test
    void answersRejectionsWith429RetryAfterAndJsonBody() throws Exception {
        // AUTH capacity is 10 per IP - exhaust it first.
        for (int i = 0; i < 10; i++) {
            filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(Long.parseLong(response.getHeader("Retry-After")) > 0,
                "Retry-After must be a positive number of seconds");
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains(
                "{\"error\":\"Too many requests\","
                        + "\"message\":\"Please wait a moment and try again.\","
                        + "\"retryAfterSeconds\":"));
        assertNull(chain.getRequest(), "a rejected request must not reach the chain");
    }

    @Test
    void forwardsEverythingWhenDisabled() throws Exception {
        properties.setEnabled(false);

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest(), "disabled filter must forward the request");
        assertEquals(0, service.getTrackedBucketCount(), "no bucket may be created when disabled");
    }
}
