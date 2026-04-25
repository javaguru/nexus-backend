/*
 * Copyright (C) 2001-2026 JServlet.com Franck Andriano.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package com.jservlet.nexus.config.web;

import com.jservlet.nexus.shared.exceptions.NexusResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * CircuitBreaker Resilience4 Configuration
 * <br>
 * States status:<br>
 * <pre>
 * - CLOSED --> OPEN 20 calls and Error > 30% Error communication --> 30s OPEN Backend unavailable --> HALF_OPEN
 *          --> HALF_OPEN                --> Error < 30% CLOSED
 *                                       --> OPEN 30s Error communication --> 30s OPEN Backend unavailable
 *                                                                        --> HALF_OPEN --> Error < 30% CLOSED
 * </pre>
 */
@Configuration
public class Resilience4jConfig {

    final static Logger logger = LoggerFactory.getLogger(Resilience4jConfig.class);

    /*
     *  % 0-100 of errors for OPEN 50% = open if 5/10 fail
     */
    @Value("${nexus.backend.circuitbreaker.failureRateThreshold:30}") // More strict 30, less 50
    private int failureRateThreshold;
    /*
     * Minimum number of calls before evaluating the error rate
     * (After 20 fails past in mode "Fail Fast" no threads, no sockets. Error communication to temporarily unavailable!)
     */
    @Value("${nexus.backend.circuitbreaker.minimumNumberOfCalls:20}") // More call before a decision 20, Sensitive 5
    private int minimumNumberOfCalls;
    /*
     * Time before retrying HALF_OPEN 30s = wait 30s before retrying (in milliseconds)
     */
    @Value("${nexus.backend.circuitbreaker.waitDurationInOpenState:30000}") // long 30s, short 5s
    private int waitDurationInOpenState;
    /*
     * Sliding window size (number of calls to remember)
     */
    @Value("${nexus.backend.circuitbreaker.slidingWindowSize:20}") // queries 20
    private int slidingWindowSize;
    /*
     * Test queries in HALF_OPEN 3 = test with 3 queries
     *
     */
    @Value("${nexus.backend.circuitbreaker.permittedNumberOfCalls:3}")  // queries 3
    private int permittedNumberOfCalls;
    /*
     * 1 initial call + 2 retry attempts
     */
    @Value("${nexus.backend.circuitbreaker.retry.maxAttempts:3}") // retry 3
    private int maxAttempts;
    /*
     * Wait 500ms between each attempt
     */
    @Value("${nexus.backend.circuitbreaker.retry.waitDuration:1000}")
    private int waitDuration;


    @Bean
    public CircuitBreaker myCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        logger.warn("Starting CircuitBreaker");

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold) // % of errors for OPEN
                .minimumNumberOfCalls(minimumNumberOfCalls) // 3 = test with 3 queries
                .waitDurationInOpenState(Duration.ofMillis(waitDurationInOpenState)) // Time before HALF_OPEN trial
                .slidingWindowSize(slidingWindowSize) // Number of queries analyzed
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCalls) // Test queries in HALF_OPEN
                .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("nexusBackendService", config);
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                logger.warn("CircuitBreaker '{}' is OPEN. Requests are blocked due to failures.", event.getCircuitBreakerName());
            }
        });

        return circuitBreaker;
    }

    @Bean
    public Retry myRetry(RetryRegistry retryRegistry) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxAttempts) // 1 initial call + 2 retry attempts
                .waitDuration(Duration.ofMillis(waitDuration))
                // Optional: Do not retry on certain exceptions (e.g., 404)
                .ignoreExceptions(NexusResourceNotFoundException.class)
                .build();

        return retryRegistry.retry("backendServiceRetry", config);
    }
}
