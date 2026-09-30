package com.carticom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private String allowedOriginsProp;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Production origins first, then local development. Extra origins can be added
        // per-deployment with the CORS_ALLOWED_ORIGINS environment variable (see
        // cors.allowed-origins in application.yml) instead of editing this class.
        List<String> origins = new ArrayList<>(List.of(
                "https://carticom.cv",
                "https://www.carticom.cv",
                "https://carticom-backend.pxxlspace.cv",
                // Live Vercel deployment URL — kept so the site keeps working from the
                // *.vercel.app host until the carticom.cv domain is fully switched over.
                "https://carticom.vercel.app",
                // Local development
                "http://localhost:3000",
                "http://localhost:3001",
                "http://127.0.0.1:3000"
        ));
        if (allowedOriginsProp != null && !allowedOriginsProp.isBlank()) {
            for (String origin : allowedOriginsProp.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !origins.contains(trimmed)) {
                    origins.add(trimmed);
                }
            }
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
