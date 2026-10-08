package com.carticom.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * Registers a filter that sets security headers on every response.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so headers are present even on
 * responses produced by the security filter chain (401/403) and by the
 * {@code GlobalExceptionHandler}.
 *
 * <p>The CSP uses {@code frame-ancestors 'self'} to stay consistent with the
 * frontend's {@code /store/preview} route, which is embedded in a same-origin
 * iframe by the Storefront Studio (see {@code next.config.ts}).
 */
@Configuration
public class SecurityHeadersConfig {

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SecurityHeadersFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("securityHeadersFilter");
        return registration;
    }

    public static class SecurityHeadersFilter implements Filter {

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req,
                             jakarta.servlet.ServletResponse res,
                             FilterChain chain) throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-XSS-Protection", "0");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

            if (request.isSecure()) {
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }

            response.setHeader("Content-Security-Policy",
                    "default-src 'self'; "
                            + "img-src 'self' data: blob: https: http:; "
                            + "style-src 'self' 'unsafe-inline'; "
                            + "script-src 'self'; "
                            + "connect-src 'self' https:; "
                            + "frame-ancestors 'self'");

            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/api/")) {
                response.setHeader("Cache-Control", "no-store");
            }

            chain.doFilter(req, res);
        }
    }
}
