package com.carticom.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * Writes audit-log entries for sensitive actions via SLF4J.
 *
 * <p>Each entry captures the actor (email + role), the action, the target,
 * the client IP and a timestamp. The service is intentionally log-only (no
 * persistence) — an {@code AuditLog} entity can be added later without
 * changing the call sites.
 *
 * <p>All public methods are non-blocking: any failure is caught and logged
 * at WARN level so an audit malfunction never breaks the main request flow.
 */
@Slf4j
@Service
public class AuditService {

    // ── Store ───────────────────────────────────────────────────────

    public void storeCreated(String actor, String role, Long storeId, String storeName) {
        log(actor, role, "store.created", "store:" + storeId + ":" + storeName);
    }

    public void storeUpdated(String actor, String role, Long storeId, String storeName) {
        log(actor, role, "store.updated", "store:" + storeId + ":" + storeName);
    }

    public void storeDeleted(String actor, String role, Long storeId, String storeName) {
        log(actor, role, "store.deleted", "store:" + storeId + ":" + storeName);
    }

    public void brandingChanged(String actor, String role, Long storeId, String storeName) {
        log(actor, role, "branding.changed", "store:" + storeId + ":" + storeName);
    }

    public void statusChanged(String actor, String role, Long storeId, String storeName, String newStatus) {
        log(actor, role, "status.changed", "store:" + storeId + ":" + storeName + " -> " + newStatus);
    }

    // ── Auth / user ─────────────────────────────────────────────────

    public void userRegistered(String actor, String role) {
        log(actor, role, "user.registered", "user:" + actor);
    }

    public void userLoggedIn(String actor, String role) {
        log(actor, role, "user.logged_in", "user:" + actor);
    }

    public void userLoggedOut(String actor, String role) {
        log(actor, role, "user.logged_out", "user:" + actor);
    }

    public void sessionRefreshed(String actor, String role) {
        log(actor, role, "session.refreshed", "user:" + actor);
    }

    public void passwordChanged(String actor, String role) {
        log(actor, role, "password.changed", "user:" + actor);
    }

    public void profileUpdated(String actor, String role) {
        log(actor, role, "profile.updated", "user:" + actor);
    }

    // ── Payment ─────────────────────────────────────────────────────

    public void paymentInitiated(String actor, String role, String reference) {
        log(actor, role, "payment.initiated", "payment:" + reference);
    }

    public void paymentConfirmed(String actor, String role, String reference) {
        log(actor, role, "payment.confirmed", "payment:" + reference);
    }

    // ── Core ────────────────────────────────────────────────────────

    /**
     * Writes a single audit-log line. Never throws.
     */
    public void log(String actor, String role, String action, String target) {
        try {
            String ip = getClientIp();
            log.info("AUDIT actor={} role={} action={} target={} ip={} timestamp={}",
                    actor, role, action, target, ip, LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to write audit log for action={}: {}", action, e.getMessage());
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    return xff.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "unknown";
    }
}
