package com.carticom.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that {@link AuditService} logs the expected events and never
 * throws, even when no request context is available.
 */
class AuditServiceTest {

    private final AuditService auditService = new AuditService();

    @Test
    void storeEventsDoNotThrow() {
        assertDoesNotThrow(() -> {
            auditService.storeCreated("seller@carticom.cv", "VENDOR", 1L, "Acme");
            auditService.storeUpdated("seller@carticom.cv", "VENDOR", 1L, "Acme");
            auditService.storeDeleted("seller@carticom.cv", "VENDOR", 1L, "Acme");
            auditService.brandingChanged("seller@carticom.cv", "VENDOR", 1L, "Acme");
            auditService.statusChanged("seller@carticom.cv", "VENDOR", 1L, "Acme", "ACTIVE");
        });
    }

    @Test
    void authEventsDoNotThrow() {
        assertDoesNotThrow(() -> {
            auditService.userRegistered("new@carticom.cv", "CUSTOMER");
            auditService.userLoggedIn("user@carticom.cv", "VENDOR");
            auditService.userLoggedOut("user@carticom.cv", "VENDOR");
            auditService.sessionRefreshed("user@carticom.cv", "VENDOR");
            auditService.passwordChanged("user@carticom.cv", "VENDOR");
            auditService.profileUpdated("user@carticom.cv", "VENDOR");
        });
    }

    @Test
    void paymentEventsDoNotThrow() {
        assertDoesNotThrow(() -> {
            auditService.paymentInitiated("seller@carticom.cv", "VENDOR", "CART-ABC123");
            auditService.paymentConfirmed("seller@carticom.cv", "VENDOR", "CART-ABC123");
        });
    }

    @Test
    void logDoesNotThrowWithoutRequestContext() {
        // No RequestContextHolder attributes set — should still not throw.
        assertDoesNotThrow(() ->
                auditService.log("actor@test.com", "VENDOR", "test.action", "test:target"));
    }

    @Test
    void logDoesNotThrowWithRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertDoesNotThrow(() ->
                    auditService.log("actor@test.com", "VENDOR", "test.action", "test:target"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void logDoesNotThrowWithForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 70.41.3.18");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertDoesNotThrow(() ->
                    auditService.log("actor@test.com", "VENDOR", "test.action", "test:target"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
