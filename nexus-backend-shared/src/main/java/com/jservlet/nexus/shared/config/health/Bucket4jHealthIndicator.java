package com.jservlet.nexus.shared.config.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.CacheManager;

@Component("bucket4jRateLimiter")
@ConditionalOnProperty(value = "nexus.api.backend.interceptor.ratelimit.enabled")
public class Bucket4jHealthIndicator implements HealthIndicator {

    private final CacheManager cacheManager;
    private static final String JCACHE_KEY = "rate-limit-buckets";

    public Bucket4jHealthIndicator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public Health health() {
        try {
            Cache<String, byte[]> cache = cacheManager.getCache(JCACHE_KEY);

            if (cache != null) {
                return Health.up()
                        .withDetail("provider", "Bucket4j")
                        .withDetail("storage", "JCache")
                        .withDetail("cacheName", JCACHE_KEY)
                        .withDetail("status", "UP")
                        .build();
            } else {
                return Health.down()
                        .withDetail("status", "DOWN")
                        .withDetail("error", "Cache '" + JCACHE_KEY + "' not found")
                        .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("status", "UNKNOWN")
                    .withDetail("error", "Communication error with JCache")
                    .build();
        }
    }
}
