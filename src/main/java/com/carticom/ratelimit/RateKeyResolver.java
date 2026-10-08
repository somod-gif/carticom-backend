package com.carticom.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the bucket key for a request: the signed-in user when the security context
 * holds one (JwtAuthenticationFilter runs before the rate limit filter), otherwise
 * the client IP.
 *
 * <p>IP resolution is proxy aware: the first {@code X-Forwarded-For} entry is the
 * real client behind a load balancer, then {@code X-Real-IP}, and finally the socket
 * address. Note that {@code server.forward-headers-strategy: framework} is already
 * enabled in application.yml, so Spring's own {@code ForwardedHeaderFilter} rewrites
 * {@code getRemoteAddr()} too - the header lookup here keeps keying correct even if
 * that strategy is ever switched off, and shared proxy addresses therefore never
 * collapse every visitor into a single bucket.
 */
@Slf4j
@Component
public class RateKeyResolver {

    /**
     * Key for tiers metered per signed-in user; anonymous callers fall back to their IP.
     */
    public String userKey(HttpServletRequest request) {
        String user = authenticatedUser();
        return user != null ? "user:" + user : "ip:" + clientIp(request);
    }

    /**
     * Key for tiers metered per client IP regardless of authentication state.
     */
    public String ipKey(HttpServletRequest request) {
        return "ip:" + clientIp(request);
    }

    /**
     * Resolves the key a tier is metered on using its {@link RateLimitTier.KeyScope}.
     */
    public String resolve(RateLimitTier tier, HttpServletRequest request) {
        return switch (tier.getKeyScope()) {
            case IP -> ipKey(request);
            case USER -> userKey(request);
        };
    }

    /**
     * The authenticated principal's name - the app identifies users by email, which is
     * exactly what JwtAuthenticationFilter puts into the security context - or null when
     * the caller is anonymous. No repository lookup, so this stays free on every request.
     */
    private String authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = firstEntry(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            return forwarded;
        }
        String realIp = firstEntry(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * First usable entry of a possibly comma separated header value. Bounded so a
     * client cannot force large strings into the bucket map with a crafted header.
     */
    private String firstEntry(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String first = header.split(",")[0].trim();
        if (first.isEmpty() || first.length() > 100) {
            return null;
        }
        return first;
    }
}
