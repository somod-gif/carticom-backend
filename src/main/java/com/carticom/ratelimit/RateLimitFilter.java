package com.carticom.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies the per-tier token buckets to incoming requests and answers rejections
 * with 429 + {@code Retry-After} + a small JSON body.
 *
 * <p>Registered in the security chain right after {@code JwtAuthenticationFilter},
 * so the {@code SecurityContext} is already populated when the bucket key is resolved.
 * When {@code ratelimit.enabled=false} the filter short-circuits and forwards
 * everything untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** Methods treated as writes by the WRITE tier. */
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Never metered: payment callbacks, schedulers and the observability/docs surface. */
    private static final List<String> EXEMPT_PATTERNS = List.of(
            "/api/v1/payments/webhook/**",
            "/api/v1/waitlist/**",
            "/actuator/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**");

    /** 429 - the Servlet API does not define a constant for it. */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    /**
     * Ordered tier matchers - first match wins. Auth before uploads, uploads before
     * public reads, and the catch-alls (WRITE then DEFAULT) last.
     */
    private static final List<TierRule> TIER_RULES = buildTierRules();

    private static final List<PathPattern> EXEMPT = buildPatterns(EXEMPT_PATTERNS);

    private final RateLimitProperties properties;
    private final RateLimitService rateLimitService;
    private final RateKeyResolver rateKeyResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = normalizePath(request);
        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitTier tier = resolveTier(request.getMethod(), path);
        RateLimitResult result = rateLimitService.tryConsume(
                tier, rateKeyResolver.resolve(tier, request));
        if (result.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(request, tier, result, response);
    }

    /**
     * Stable marker name: Spring Boot also registers every Filter bean with the servlet
     * container, and that copy must see the marker set by the copy inside the security
     * chain (they are nested, so the marker is still set) and skip instead of charging
     * the request a second token.
     */
    @Override
    protected String getAlreadyFilteredAttributeName() {
        return RateLimitFilter.class.getName() + ".FILTERED";
    }

    /**
     * True when the path is never rate limited.
     */
    boolean isExempt(String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (PathPattern pattern : EXEMPT) {
            if (pattern.matches(pathContainer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * First matching tier wins; anything unmatched falls through to {@code DEFAULT}.
     */
    RateLimitTier resolveTier(String method, String path) {
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (TierRule rule : TIER_RULES) {
            if (rule.matches(method, pathContainer)) {
                return rule.tier();
            }
        }
        return RateLimitTier.DEFAULT;
    }

    /**
     * The ordered matcher list, exposed so tests (and reviewers) can read the table.
     */
    static List<TierRule> tierRules() {
        return TIER_RULES;
    }

    /**
     * Writes the 429 response directly: {@link com.carticom.config.GlobalResponseWrapper}
     * only passes through Maps that carry an "error" key, and writing to the response
     * here bypasses the advice anyway, so the body must carry the exact agreed shape.
     */
    private void reject(HttpServletRequest request, RateLimitTier tier,
                        RateLimitResult result, HttpServletResponse response) throws IOException {
        if (tier == RateLimitTier.AUTH) {
            log.info("Rate limited {} {} on tier {} (retry after {}s)",
                    request.getMethod(), request.getRequestURI(), tier, result.retryAfterSeconds());
        } else {
            log.debug("Rate limited {} {} on tier {} (retry after {}s)",
                    request.getMethod(), request.getRequestURI(), tier, result.retryAfterSeconds());
        }

        response.setStatus(HTTP_TOO_MANY_REQUESTS);
        response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Too many requests\","
                + "\"message\":\"Please wait a moment and try again.\","
                + "\"retryAfterSeconds\":" + result.retryAfterSeconds() + "}");
    }

    /**
     * Request URI without the context path and without trailing slashes, so
     * {@code /api/v1/auth/login/} is metered as {@code /api/v1/auth/login}.
     */
    private String normalizePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path == null || path.isEmpty()) {
            return "/";
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static List<PathPattern> buildPatterns(List<String> patterns) {
        List<PathPattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            compiled.add(PathPatternParser.defaultInstance.parse(pattern));
        }
        return List.copyOf(compiled);
    }

    private static List<TierRule> buildTierRules() {
        List<TierRule> rules = new ArrayList<>();

        // AUTH - credential stuffing and invite abuse, keyed by IP.
        for (String pattern : List.of("/api/v1/auth/login", "/api/v1/auth/register",
                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password")) {
            rules.add(rule("auth", Set.of(), pattern, RateLimitTier.AUTH));
        }
        rules.add(rule("auth", Set.of(), "/api/v1/invites/*/accept", RateLimitTier.AUTH));

        // UPLOAD - multipart uploads, keyed by the signed-in seller.
        rules.add(rule("upload", Set.of(), "/api/v1/images/upload", RateLimitTier.UPLOAD));
        rules.add(rule("upload", Set.of(), "/api/v1/storage/upload", RateLimitTier.UPLOAD));
        rules.add(rule("upload", Set.of("POST"), "/api/v1/stores/*/logo", RateLimitTier.UPLOAD));
        rules.add(rule("upload", Set.of("POST"), "/api/v1/stores/*/banner", RateLimitTier.UPLOAD));

        // AI - expensive upstream calls, keyed by the signed-in user.
        for (String pattern : List.of("/api/v1/ai/chat", "/api/v1/ai/generate-description",
                "/api/v1/ai/insights")) {
            rules.add(rule("ai", Set.of(), pattern, RateLimitTier.AI));
        }

        // PUBLIC - anonymous storefront browsing, keyed by IP.
        for (String pattern : List.of("/api/v1/storefront/**", "/api/v1/buyer/**",
                "/api/v1/cart/**", "/api/v1/checkout/**", "/api/v1/images/file/**",
                "/api/v1/orders/track/**")) {
            rules.add(rule("public", Set.of(), pattern, RateLimitTier.PUBLIC));
        }

        // WRITE - every other mutating request, DEFAULT - reads and anything else.
        rules.add(rule("write", MUTATING_METHODS, "/**", RateLimitTier.WRITE));
        rules.add(rule("default", Set.of(), "/**", RateLimitTier.DEFAULT));
        return List.copyOf(rules);
    }

    private static TierRule rule(String name, Set<String> methods, String pattern, RateLimitTier tier) {
        return new TierRule(name, methods, PathPatternParser.defaultInstance.parse(pattern), tier);
    }

    /**
     * One row of the tier table: a label, the HTTP methods it applies to (empty = any),
     * the compiled path pattern and the tier that path is metered as. Compiled once at
     * class load, so matching is allocation free per request.
     */
    record TierRule(String name, Set<String> methods, PathPattern pattern, RateLimitTier tier) {

        boolean matches(String method, PathContainer pathContainer) {
            return (methods.isEmpty() || methods.contains(method)) && pattern.matches(pathContainer);
        }
    }
}
