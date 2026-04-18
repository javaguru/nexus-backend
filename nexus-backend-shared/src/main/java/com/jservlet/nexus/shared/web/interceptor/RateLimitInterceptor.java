package com.jservlet.nexus.shared.web.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.grid.jcache.JCacheProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * This interceptor checks incoming requests against a rate limit defined on a per-IP-address basis.
 */
@ConditionalOnProperty(value = "nexus.api.backend.interceptor.ratelimit.enabled")
public class RateLimitInterceptor extends ApiBase implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private static final String SOURCE = "INTERNAL-LIMIT-NEXUS-BACKEND";

    private static final String JCACHE_KEY = "rate-limit-buckets";

    // A thread-safe map to store a bucket for each IP address.
    private final ProxyManager<String> proxyManager;

    private ObjectMapper objectMapper;
    private int refillToken = 1000;
    private int refillMinutes = 1;
    private int bandwidthCapacity = 1000;

     /**
     * Injection du CacheManager JCache (Standard javax.cache)
     */
    public RateLimitInterceptor(CacheManager cacheManager) {
        super(SOURCE);
        logger.info("Starting Bucket4j RateLimit");
        Cache<String, byte[]> cache = cacheManager.getCache(JCACHE_KEY);
        if (cache == null) {
            logger.info("Load Dynamic cache JCache: {}", JCACHE_KEY);
            MutableConfiguration<String, byte[]> config = new MutableConfiguration<>();
            config.setTypes(String.class, byte[].class);
            cache = cacheManager.createCache(JCACHE_KEY, config);
        } else {
            logger.info("Load cache JCache: {}", JCACHE_KEY);
        }
        this.proxyManager = new JCacheProxyManager<>(cache);
    }

    /**
     * Configuration Bucket
     */
    private BucketConfiguration createNewBucketConfig() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(bandwidthCapacity)
                .refillGreedy(refillToken, Duration.ofMinutes(refillMinutes))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }


    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {

        String ip = request.getRemoteAddr();

        // Get or create a bucket for the client's IP address, only once in a thread-safe way.
        Bucket bucket = proxyManager.builder().build(ip, this::createNewBucketConfig);

        if (bucket.tryConsume(1)) {
            return true;
        } else {
            HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Message msg = new Message(String.valueOf(status.value()), "ERROR", SOURCE,
                    "Request rejected due to rate limited request quota.");
            byte[] responseBody = objectMapper.writeValueAsBytes(msg);

            logger.warn("{} - RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                    msg.getMessage(), request.getRemoteHost(), request.getMethod(),
                    request.getServletPath(), request.getHeader("User-Agent"));

            response.getOutputStream().write(responseBody);
            response.flushBuffer();
            return false;
        }
    }


    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setRefillToken(int refillToken) {
        this.refillToken = refillToken;
    }

    public void setRefillMinutes(int refillMinutes) {
        this.refillMinutes = refillMinutes;
    }

    public void setBandwidthCapacity(int bandwidthCapacity) {
        this.bandwidthCapacity = bandwidthCapacity;
    }
}
