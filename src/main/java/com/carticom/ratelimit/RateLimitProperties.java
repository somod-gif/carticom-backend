package com.carticom.ratelimit;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rate limiter configuration, bound from the {@code ratelimit.*} section of
 * application.yml. Every value there is declared with a {@code ${RATELIMIT_*:default}}
 * placeholder, so a deployment can retune any tier through the environment without
 * touching code.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    /** Master switch - when false the filter forwards every request untouched. */
    private boolean enabled = true;

    /** Hard cap on tracked (tier, key) buckets; least recently used are evicted past it. */
    private int maxEntries = 50_000;

    /** Buckets untouched for this many seconds are dropped by the scheduled cleanup. */
    private long bucketIdleSeconds = 600;

    /**
     * Optional per-tier overrides keyed by lower-case tier name
     * (auth, upload, ai, public, write, default). Fields left unset inherit the
     * defaults declared on {@link RateLimitTier}.
     */
    private Map<String, Tier> tiers = new HashMap<>();

    /**
     * Effective bucket shape for a tier: the deployment override merged over the
     * built-in defaults.
     */
    public Tier resolve(RateLimitTier tier) {
        Tier override = tiers != null ? tiers.get(tier.name().toLowerCase(Locale.ROOT)) : null;
        Tier defaults = tier.getDefaults();
        if (override == null) {
            return copy(defaults);
        }
        return new Tier(
                override.getCapacity() != null ? override.getCapacity() : defaults.getCapacity(),
                override.getRefillTokens() != null ? override.getRefillTokens() : defaults.getRefillTokens(),
                override.getRefillMinutes() != null ? override.getRefillMinutes() : defaults.getRefillMinutes());
    }

    private static Tier copy(Tier source) {
        return new Tier(source.getCapacity(), source.getRefillTokens(), source.getRefillMinutes());
    }

    /**
     * Shape of a token bucket: {@code capacity} tokens of burst, then a steady refill
     * of {@code refillTokens} every {@code refillMinutes}. With a one minute window
     * {@code refillTokens} is simply "tokens per minute".
     *
     * <p>Values are boxed so the yml override can set a single field and leave the
     * rest at the tier default. Zero or negative values are normalised to 1 when the
     * bucket is built, so a bad environment variable can never disable refilling
     * by accident.
     */
    @Data
    @NoArgsConstructor
    public static class Tier {

        private Long capacity;
        private Long refillTokens;
        private Long refillMinutes;

        public Tier(long capacity, long refillTokens, long refillMinutes) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillMinutes = refillMinutes;
        }
    }
}
