package com.carticom.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Holds one token bucket per {@code tier|key} pair and answers rate-limit checks.
 * The buckets are Bucket4j local buckets (lock free, nanotime refill), so concurrent
 * requests from the same user or IP are counted correctly.
 *
 * <p>The map is capped and idle entries are swept every minute, which keeps memory
 * bounded even under a scanner burst that would otherwise mint a bucket per bogus key.
 *
 * <p>{@code @EnableScheduling} switches the scheduler on for the whole context - the
 * long dormant {@code CartService#markAbandonedCarts} job starts running too, which is
 * what its own {@code @Scheduled} annotation always asked for.
 */
@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class RateLimitService {

    /** Never let the map grow past this share of the cap in a single eviction round. */
    private static final int EVICTION_BATCH_PERCENT = 5;

    private final RateLimitProperties properties;

    /** (tier + "|" + key) -> bucket plus its last touch time, for idle eviction. */
    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    /**
     * Checks one request against a tier's bucket, creating the bucket on first use.
     *
     * @param tier bucket shape and key scope to apply
     * @param key  resolved bucket key, see {@link RateKeyResolver}
     */
    public RateLimitResult tryConsume(RateLimitTier tier, String key) {
        String bucketKey = tier.name() + "|" + key;
        BucketEntry entry = buckets.get(bucketKey);
        if (entry == null) {
            entry = getOrCreateBucket(bucketKey, tier);
        }

        long now = System.nanoTime();
        ConsumptionProbe probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        entry.touch(now);

        if (probe.isConsumed()) {
            return RateLimitResult.pass();
        }
        long waitSeconds = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000d);
        return RateLimitResult.fail(waitSeconds);
    }

    /**
     * Drops buckets nobody has touched for {@code ratelimit.bucket-idle-seconds}.
     * A refilled-but-untouched bucket is worthless, so losing one costs nothing: the
     * next request simply starts a fresh bucket.
     */
    @Scheduled(fixedRate = 60000)
    public void evictIdleBuckets() {
        if (buckets.isEmpty()) {
            return;
        }
        long idleNanos = TimeUnit.SECONDS.toNanos(properties.getBucketIdleSeconds());
        long now = System.nanoTime();
        int evicted = 0;
        for (Map.Entry<String, BucketEntry> mapEntry : buckets.entrySet()) {
            if (now - mapEntry.getValue().lastAccessNanos() > idleNanos) {
                buckets.remove(mapEntry.getKey(), mapEntry.getValue());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("Rate limit: evicted {} idle buckets, {} still tracked", evicted, buckets.size());
        }
    }

    /**
     * Number of buckets currently held - exposed for tests and monitoring.
     */
    public int getTrackedBucketCount() {
        return buckets.size();
    }

    private BucketEntry getOrCreateBucket(String bucketKey, RateLimitTier tier) {
        makeRoom();
        BucketEntry created = new BucketEntry(buildBucket(tier), System.nanoTime());
        BucketEntry existing = buckets.putIfAbsent(bucketKey, created);
        return existing != null ? existing : created;
    }

    private Bucket buildBucket(RateLimitTier tier) {
        RateLimitProperties.Tier config = properties.resolve(tier);
        long capacity = Math.max(1, valueOr(config.getCapacity()));
        long refillTokens = Math.max(1, valueOr(config.getRefillTokens()));
        long refillMinutes = Math.max(1, valueOr(config.getRefillMinutes()));

        Bandwidth bandwidth = Bandwidth.classic(capacity,
                Refill.greedy(refillTokens, Duration.ofMinutes(refillMinutes)));
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private long valueOr(Long candidate) {
        return candidate != null ? candidate : 1L;
    }

    /**
     * Keeps the bucket map inside its cap: sweep idle entries first, then - when the
     * map really is saturated with live keys - drop the least recently used ones in
     * batches, so a burst of new keys cannot grow memory without bound.
     */
    private void makeRoom() {
        int maxEntries = Math.max(1, properties.getMaxEntries());
        if (buckets.size() < maxEntries) {
            return;
        }
        evictIdleBuckets();
        if (buckets.size() < maxEntries) {
            return;
        }

        int evictCount = Math.max(1, maxEntries * EVICTION_BATCH_PERCENT / 100);
        List<String> leastRecentlyUsed = buckets.entrySet().stream()
                .sorted(Comparator.comparingLong(mapEntry -> mapEntry.getValue().lastAccessNanos()))
                .limit(evictCount)
                .map(Map.Entry::getKey)
                .toList();
        for (String key : leastRecentlyUsed) {
            BucketEntry entry = buckets.get(key);
            if (entry != null) {
                buckets.remove(key, entry);
            }
        }
        log.warn("Rate limit: cap of {} tracked buckets reached, evicted {} least recently used",
                maxEntries, leastRecentlyUsed.size());
    }

    /**
     * A bucket plus the last time a request touched it, used for idle eviction.
     */
    private static final class BucketEntry {

        private final Bucket bucket;
        private volatile long lastAccessNanos;

        private BucketEntry(Bucket bucket, long lastAccessNanos) {
            this.bucket = bucket;
            this.lastAccessNanos = lastAccessNanos;
        }

        private Bucket bucket() {
            return bucket;
        }

        private long lastAccessNanos() {
            return lastAccessNanos;
        }

        private void touch(long now) {
            this.lastAccessNanos = now;
        }
    }
}
