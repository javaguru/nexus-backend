/*
 * Copyright (C) 2001-2024 JServlet.com Franck Andriano.
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
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.ErrorMessage;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.jservlet.nexus.shared.web.filter.WAFFilter.Reactive.*;


/**
 * WebFilter class implements a secure WAF protection for request Body.
 * Http request Cookies, Headers, Parameters and Body can be filtered.
 * <p>
 * Un-normalized requests are automatically rejected by the StrictHttpFirewall,
 * and path parameters and duplicate slashes are removed for matching purposes.
 * <p>
 * Noted the valid characters are defined in RFC 7230 and RFC 3986 are checked
 * by the Apache Coyote http11 processor (see coyote Error parsing HTTP request header)
 * <p>
 *     Default reactive mode is STRICT mode
 * <ul>
 *     <li>STRICT:  StrictHttpFirewall + RequestBody</li>
 *     <li>PASSIVE: StrictHttpFirewall + Clean RequestBody and parameters Map</li>
 *     <li>UNSAFE:  StrictHttpFirewall + No check RequestBody!</li>
 * </ul>
 * <p>
 * Activated WebFilter by only 'nexus.api.backend.filter.waf.enabled=true' in the configuration
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.filter.waf.enabled")
public class WAFFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(WAFFilter.class);

    FilterConfig filterConfig = null;

    /**
     * Default STRICT mode, PASSIVE or UNSAFE!
     * STRICT:  Strict HttpFirewall + Json RequestBody
     * PASSIVE: Strict HttpFirewall + Clean Json RequestBody and Parameters Map
     * UNSAFE:  Strict HttpFirewall + No check Json RequestBody!
     */
    @Value("${nexus.api.backend.filter.waf.reactive.mode:STRICT}")
    private Reactive reactiveMode;

    /**
     * Deep Scan Cookie Keys/Values and Httponly
     */
    @Value("${nexus.api.backend.filter.waf.deepscan.cookie:false}")
    private boolean isDeepScanCookie;


    private final WAFPredicate wafPredicate;
    private final ObjectMapper objectMapper;

    @Autowired
    public WAFFilter(WAFPredicate wafPredicate, ObjectMapper objectMapper) {
        this.wafPredicate = wafPredicate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        logger.info("Starting " + filterConfig.getFilterName());
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException, RequestRejectedException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;
        if (!isMultipart(req)) { // WARN not multipart!
            // WAFRequestWrapper
            WAFRequestWrapper wrappedRequest;
            try {
                wrappedRequest = new WAFRequestWrapper(req);
                if (STRICT == reactiveMode) {
                    // Check the cookies!
                    Cookie[] cookies = wrappedRequest.getCookies();
                    if (cookies !=  null) {
                        if (cookies.length > 100) throw new RequestRejectedException("Cookie size reach the limit!");
                        // Deep Scan Cookie and reject if not HttpOnly!
                        if (isDeepScanCookie) {
                            for (Cookie cookie : cookies) {
                                rejectCookie(cookie);
                            }
                        }
                    }

                    // Check the Json body!
                    String body = IOUtils.toString(wrappedRequest.getReader());
                    if (!StringUtils.isBlank(body)) {
                        rejectBody(body);
                    }
                }

                if (PASSIVE == reactiveMode) {
                    // Just clean the current parameters, no evasion !
                    Map<String, String[]> map = cleanerParameterMap(wrappedRequest.getParameterMap()) ;
                    wrappedRequest.setParameterMap(map);

                    // Just clean the Json body!
                    String body = IOUtils.toString(wrappedRequest.getReader());
                    if (!StringUtils.isBlank(body)) {
                        wrappedRequest.setInputStream(stripWAFPattern(body).getBytes());
                    }
                }

                // And continue the chain filter with the wrappedRequest!
                chain.doFilter(wrappedRequest, response);
            }
            catch (RequestRejectedException ex) {
                logger.error("Intercepted RequestRejectedException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                        LogFormatUtils.formatValue(ex.getMessage(), !logger.isDebugEnabled()), // No truncated in debug mode!
                        request.getRemoteHost(), req.getMethod(), req.getServletPath(), req.getHeader("User-Agent"));

                // Request rejected!
                resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resp.setStatus(HttpStatus.BAD_REQUEST.value());
                resp.getWriter().write(objectMapper.writeValueAsString(
                        new ErrorMessage("400", "INTERNAL-NEXUS-REST-BACKEND","Request rejected!").getError()));
             }
        }
        else chain.doFilter(request, response);
    }

    public enum Reactive {
        STRICT(0),  // StrictHttpFirewall + RequestBody
        PASSIVE(1), // StrictHttpFirewall + Clean RequestBody and parameters Map
        UNSAFE(2);  // StrictHttpFirewall + No check RequestBody!

        private final int value;

        Reactive(int value) { this.value = value; }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return this.name();
        }
    }

    private String stripWAFPattern(String value) {
        if (value == null) return null;
        // matcher xssPattern replaceAll ?
        for (Pattern pattern : wafPredicate.getWafPatterns())
            value = pattern.matcher(value).replaceAll("");
        return value;
    }

    private boolean isWAFPattern(String value) {
        if (value == null) return false;
        // matcher xssPattern find ?
        for (Pattern pattern : wafPredicate.getWafPatterns())
            if (pattern.matcher(value).find())
                return true;
        return false;
    }

    /**
     * Just clean the current parameters, no evasion !
     * @return Map  Parameters
     */
    private Map<String, String[]> cleanerParameterMap(Map<String, String[]> modifiableParameters) {
        Map<String, String[]> parameters = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : modifiableParameters.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            int len = values.length;
            String[] encodedValues = new String[len];
            for (int i = 0; i < len; i++)
                encodedValues[i] = stripWAFPattern(values[i]);
            parameters.put(key, encodedValues);
        }
        return parameters;
    }

    private void rejectParameterNames(String name) {
        if (!wafPredicate.getWAFParameterNames().test(name)) {
            throw new RequestRejectedException(
                    "The request was rejected because the Parameter name \"" + name + "\" is not allowed.");
        }
    }
    private void rejectParameterValues(String value) {
        if (!wafPredicate.getWAFParameterNames().test(value)) {
            throw new RequestRejectedException(
                    "The request was rejected because the Parameter value \"" + value + "\" is not allowed.");
        }
    }
    private void rejectBody(String body) {
        if (!wafPredicate.getWAFParameterValues().test(body)) {
            throw new RequestRejectedException(
                    "The request was rejected because the Body value \"" + body + "\" is not allowed.");
        }
    }
    private void rejectCookie(Cookie cookie) {
        if (!cookie.isHttpOnly()) {
            throw new RequestRejectedException(
                    "The request was rejected because the Cookie \"" + cookie.getName()+ "\" is not HttpOnly.");
        }
        if (!wafPredicate.getWAFParameterNames().test(cookie.getName())) {
            throw new RequestRejectedException(
                    "The request was rejected because the Cookie name \"" + cookie.getName()+ "\" is not allowed.");
        }

        if (!wafPredicate.getWAFParameterValues().test(cookie.getValue())) {
            throw new RequestRejectedException(
               "The request was rejected because the Cookie value \"" + cookie.getValue()
                            + "\" with the Cookie name \"" + cookie.getName()+ "\" is not allowed.");
        }
    }

    @Override
    public void destroy() {
        this.filterConfig = null;
    }

    private static boolean isMultipart(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod()) &&
            !"PUT".equalsIgnoreCase(request.getMethod()) &&
            !"PATCH".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String contentType = request.getContentType();
        return (contentType != null && contentType.toLowerCase().startsWith("multipart/"));
    }

}
