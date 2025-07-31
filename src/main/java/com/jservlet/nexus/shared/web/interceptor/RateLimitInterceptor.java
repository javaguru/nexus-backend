package com.jservlet.nexus.shared.web.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.github.bucket4j.local.LocalBucketBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This interceptor checks incoming requests against a rate limit defined on a per-IP-address basis.
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.interceptor.ratelimit.enabled")
public class RateLimitInterceptor extends ApiBase implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private static final String SOURCE = "INTERNAL-LIMIT-NEXUS-BACKEND";

    // A thread-safe map to store a bucket for each IP address.
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    private ObjectMapper objectMapper;
    private int refillToken = 1000;
    private int refillMinutes = 1;
    private int bandwidthCapacity = 1000;

    public RateLimitInterceptor() {
        super(SOURCE);
    }

    /**
     * Creates a new bucket with a defined capacity and refill rate.
     * This example plan allows 1000 requests per minute.
     *
     * @return a new Bucket instance.
     */
    private Bucket createNewBucket() {
        // Refill 1000 tokens every 1 minute.
        Refill refill = Refill.greedy(refillToken, Duration.ofMinutes(refillMinutes));
        // The bucket capacity is 1000.
        Bandwidth limit = Bandwidth.classic(bandwidthCapacity, refill);
        return new LocalBucketBuilder().addLimit(limit).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {

        String ip = request.getRemoteAddr();

        // Get or create a bucket for the client's IP address, only once in a thread-safe way.
        Bucket bucket = cache.computeIfAbsent(ip, k -> createNewBucket());

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
