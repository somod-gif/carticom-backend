package com.carticom.config;

import com.carticom.config.SecurityHeadersConfig.SecurityHeadersFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SecurityHeadersFilter} sets the expected security
 * headers on every response.
 */
class SecurityHeadersFilterTest {

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
    }

    @Test
    void setsCoreSecurityHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stores");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("0", response.getHeader("X-XSS-Protection"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=()",
                response.getHeader("Permissions-Policy"));
    }

    @Test
    void setsCspWithFrameAncestorsSelf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stores");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String csp = response.getHeader("Content-Security-Policy");
        assertTrue(csp.contains("default-src 'self'"));
        assertTrue(csp.contains("frame-ancestors 'self'"));
        assertTrue(csp.contains("img-src 'self' data: blob: https: http:"));
        assertTrue(csp.contains("script-src 'self'"));
    }

    @Test
    void setsNoStoreForApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stores");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void doesNotSetNoStoreForNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader("Cache-Control"));
    }

    @Test
    void doesNotSetHstsForInsecureRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stores");
        // MockHttpServletRequest defaults to insecure
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(response.getHeader("Strict-Transport-Security"));
    }

    @Test
    void setsHstsForSecureRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/stores");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("max-age=31536000; includeSubDomains",
                response.getHeader("Strict-Transport-Security"));
    }
}
