/*
 * Copyright (C) 2001-2025 JServlet.com Franck Andriano.
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

package com.jservlet.nexus.shared.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * WebFilter class implements a secure WAF protection for request Body.<br>
 * Http request Cookies, Headers, Parameters and Body can be filtered.
 * <p>
 * Un-normalized requests are automatically rejected by the StrictHttpFirewall,
 * and path parameters and duplicate slashes are removed for matching purposes.<br>
 * Noted the valid characters are defined in RFC 7230 and RFC 3986 are checked
 * by the Apache Coyote http11 processor (see coyote Error parsing HTTP request header)<br>
 * <p>
 * Default reactive mode is STRICT mode
 * <ul>
 * <li>STRICT:  StrictHttpFirewall + Rejects requests with malicious patterns.</li>
 * <li>PASSIVE: StrictHttpFirewall + Cleans malicious patterns from request body and parameters.</li>
 * <li>UNSAFE:  StrictHttpFirewall + No checks on request body.</li>
 * </ul>
 * <p>
 * Activated WebFilter by only 'nexus.api.backend.filter.waf.enabled=true' in the configuration
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.filter.waf.enabled")
public class WAFFilter extends ApiBase implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(WAFFilter.class);

    private static final String SOURCE = "INTERNAL-REST-NEXUS-BACKEND";

    /**
     * Defines the operational mode of the WAF.
     */
    public enum Reactive {
        STRICT,  // Rejects requests with malicious patterns.
        PASSIVE, // Cleans malicious patterns from the request.
        UNSAFE   // Performs no checks on the request body.
    }

    @Value("${nexus.api.backend.filter.waf.reactive.mode:STRICT}")
    private Reactive reactiveMode;

    @Value("${nexus.api.backend.filter.waf.deepscan.cookie:false}")
    private boolean isDeepScanCookie;

    private final WAFPredicate wafPredicate;
    private final ObjectMapper objectMapper;

    public WAFFilter(WAFPredicate wafPredicate, ObjectMapper objectMapper) {
        super(SOURCE);
        this.wafPredicate = wafPredicate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        logger.info("Starting WAF Filter with reactive mode: {}", reactiveMode);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;

        try {
            if (reactiveMode == Reactive.UNSAFE) { // WARN UNSAFE mode bypasses all checks.
                chain.doFilter(req, resp);
                return;
            }

            // Wrap the request to allow reading input stream multiple times and modifying parameters
            WAFRequestWrapper wrappedRequest = new WAFRequestWrapper(req);

            // Validate User-Agent
            validateUserAgent(wrappedRequest);

            if (reactiveMode == Reactive.STRICT) {
                handleStrict(wrappedRequest);
            } else if (reactiveMode == Reactive.PASSIVE) {
                handlePassive(wrappedRequest);
            }

            // Continue the filter chain with the (potentially wrapped) request.
            chain.doFilter(wrappedRequest, response);

        } catch (RequestRejectedException ex) {
            handleRequestRejected(ex, req, resp);
        }
    }

    /**
     * Handles STRICT mode. Rejects requests on pattern match.
     */
    private void handleStrict(WAFRequestWrapper wrappedRequest) throws IOException {
        // Check cookies if deep scan is enabled.
        if (isDeepScanCookie) {
            validateCookies(wrappedRequest);
        }

        // Check the request body for malicious patterns.
        String body = IOUtils.toString(wrappedRequest.getReader());
        if (!StringUtils.isBlank(body)) {
            rejectBodyIfInvalid(body);
        }
    }

    /**
     * Handles PASSIVE mode. Cleans the request on pattern match.
     */
    private void handlePassive(WAFRequestWrapper wrappedRequest) throws IOException {
        // Clean XSS patterns from request parameters.
        Map<String, String[]> cleanedParameters = cleanParameterMap(wrappedRequest.getParameterMap());
        wrappedRequest.setParameterMap(cleanedParameters);

        // Clean XSS patterns from the request body.
        String body = IOUtils.toString(wrappedRequest.getReader());
        if (!StringUtils.isBlank(body)) {
            String cleanedBody = stripWAFPatterns(body, wafPredicate.getXSSPatterns());
            wrappedRequest.setInputStream(cleanedBody.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Validates all cookies in the request if deep scan is enabled.
     */
    private void validateCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length > 100) {
                throw new RequestRejectedException("Request rejected: Exceeded cookie limit (100).");
            }
            for (Cookie cookie : cookies) {
                rejectCookieIfInvalid(cookie);
            }
        }
    }

    /**
     * Validates the User-Agent header if configured to do so.
     */
    private void validateUserAgent(HttpServletRequest request) {
        if (wafPredicate.isBlockDisallowedUserAgents()) {
            String userAgent = request.getHeader("User-Agent");
            if (wafPredicate.isUserAgentBlocked(userAgent)) {
                throw new RequestRejectedException("Request rejected: Disallowed User-Agent.");
            }
        }
    }

    /**
     * Centralized handler for sending a rejection response.
     */
    private void handleRequestRejected(RequestRejectedException ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        logger.error("Intercepted RequestRejectedException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                LogFormatUtils.formatValue(ex.getMessage(), !logger.isDebugEnabled()), // No truncated in debug mode!
                req.getRemoteHost(), req.getMethod(), req.getServletPath(), req.getHeader("User-Agent"));

        resp.setStatus(HttpStatus.BAD_REQUEST.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // Write a standard error message to the response body.
        byte[] responseBody = objectMapper.writeValueAsBytes(
                new Message("400", "ERROR", SOURCE, "Request rejected due to security policy violation.")
        );
        resp.getOutputStream().write(responseBody);
        resp.flushBuffer();
    }

    /**
     * Cleans malicious patterns from all values in a parameter map.
     * @return A new map with cleaned parameter values.
     */
    private Map<String, String[]> cleanParameterMap(Map<String, String[]> originalParameters) {
        Map<String, String[]> cleanedParameters = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : originalParameters.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            String[] cleanedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanedValues[i] = stripWAFPatterns(values[i], wafPredicate.getXSSPatterns());
            }
            cleanedParameters.put(key, cleanedValues);
        }
        return cleanedParameters;
    }

    /**
     * Removes all occurrences of the given patterns from a string.
     */
    private String stripWAFPatterns(String value, List<Pattern> patterns) {
        if (value == null) return null;
        // Prevent Regular Expression Denial of Service (ReDoS) attacks.
        if (value.length() > 100000) {
            throw new RequestRejectedException("Input value is too long for pattern stripping.");
        }
        String strippedValue = value;
        for (Pattern pattern : patterns) {
            strippedValue = pattern.matcher(strippedValue).replaceAll("");
        }
        return strippedValue;
    }

    /**
     * Throws RequestRejectedException if the request body contains disallowed patterns.
     */
    private void rejectBodyIfInvalid(String body) {
        if (!wafPredicate.getWAFParameterValues().test(body)) {
            throw new RequestRejectedException("Request rejected: Disallowed pattern found in request body.");
        }
    }

    /**
     * Throws RequestRejectedException if a cookie is invalid.
     */
    private void rejectCookieIfInvalid(Cookie cookie) {
        // Enforce HttpOnly flag for security.
        if (!cookie.isHttpOnly()) {
            throw new RequestRejectedException("Request rejected: Cookie '" + cookie.getName() + "' is not HttpOnly.");
        }
        // Validate cookie name against patterns.
        if (!wafPredicate.getWAFParameterNames().test(cookie.getName())) {
            throw new RequestRejectedException("Request rejected: Disallowed pattern in cookie name '" + cookie.getName() + "'.");
        }
        // Validate cookie value against patterns.
        if (!wafPredicate.getWAFParameterValues().test(cookie.getValue())) {
            throw new RequestRejectedException("Request rejected: Disallowed pattern in value for cookie '" + cookie.getName() + "'.");
        }
    }

    @Override
    public void destroy() {
        logger.info("Shutting down WAF Filter.");
    }
}
