package com.carticom.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every response must carry the hardening headers, /api/** responses
 * must not be cached, and HSTS is only emitted on TLS requests.
 */
class SecurityHeadersConfigTest {

    @RestController
    static class PingController {
        @GetMapping("/api/v1/ping")
        public String apiPing() {
            return "pong";
        }

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController())
                .addFilter(new SecurityHeadersConfig.SecurityHeadersFilter())
                .build();
    }

    @Test
    void setsTheStandardSecurityHeadersOnEveryResponse() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-XSS-Protection", "0"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; "
                                + "img-src 'self' data: blob: https: http:; "
                                + "style-src 'self' 'unsafe-inline'; "
                                + "script-src 'self'; "
                                + "connect-src 'self' https:; "
                                + "frame-ancestors 'self'"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void setsHstsOnlyOnSecureRequests() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));

        mockMvc.perform(get("/api/v1/ping").with(request -> {
            request.setSecure(true);
            return request;
        })).andExpect(header().string("Strict-Transport-Security",
                "max-age=31536000; includeSubDomains"));
    }

    @Test
    void onlyApiPathsGetNoStoreCacheControl() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Cache-Control"));
    }

    @Test
    void registersAtHighestPrecedence() {
        FilterRegistrationBean<SecurityHeadersConfig.SecurityHeadersFilter> registration =
                new SecurityHeadersConfig().securityHeadersFilter();

        assertEquals(Ordered.HIGHEST_PRECEDENCE, registration.getOrder());
    }
}
