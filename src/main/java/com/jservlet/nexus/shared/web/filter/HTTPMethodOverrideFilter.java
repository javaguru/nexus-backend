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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * WebFilter class that aims to remap blocked RequestMethod using an specialized http-header
 * which contain the blocked (overridden) request method.
 * The default herder name is HTTPMethodOverrideFilter.#DEFAULT_HEADER_NAME
 * <p>
 * POST: Allowed to override as PUT or PATCH request
 * GET: Allowed to override as DELETE request
 * <p>
 * The header name as well as the method override mapping can be defined using the constructor
 * HTTPMethodOverrideFilter#HTTPMethodOverrideFilter(string, map)
 * <p>
 * For example the method PUT is blocked ..
 * Send the request with POST and the header X-HTTP-Method-Override: PUT
 * <p>
 * Activated WebFilter by only 'nexus.api.backend.filter.httpoverride.enabled=true' in the configuration
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.filter.httpoverride.enabled")
public class HTTPMethodOverrideFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HTTPMethodOverrideFilter.class);

    private static final String DEFAULT_HEADER_NAME = "X-HTTP-Method-Override";

    private static final Map<HttpMethod, Set<HttpMethod>> DEFAULT_METHOD_MAPPING;

    static {
        final Map<HttpMethod, Set<HttpMethod>> defaultMethodMapping = new HashMap<>();
        defaultMethodMapping.put(HttpMethod.POST, Collections.unmodifiableSet(new HashSet<>(Arrays.asList(HttpMethod.PATCH, HttpMethod.PUT))));
        defaultMethodMapping.put(HttpMethod.GET, Collections.unmodifiableSet(new HashSet<>(Collections.singletonList(HttpMethod.DELETE))));
        DEFAULT_METHOD_MAPPING = Collections.unmodifiableMap(defaultMethodMapping);
    }

    private final String headerName;

    private final Map<String, Set<String>> methodMapping;

    public HTTPMethodOverrideFilter() { this(DEFAULT_HEADER_NAME,  DEFAULT_METHOD_MAPPING); }

    public HTTPMethodOverrideFilter(String headerName, Map<HttpMethod, Set<HttpMethod>> methodMapping) {
        Assert.hasText(headerName, "headerName may not be null!");
        this.headerName = headerName;
        this.methodMapping = convertMethodMapping(methodMapping);
        logger.info("Started MethodOverride OncePerRequest Filter");
    }

    private Map<String, Set<String>> convertMethodMapping(Map<HttpMethod, Set<HttpMethod>> methodMapping) {

        if (methodMapping != null && !methodMapping.isEmpty()) {
            final Map<String, Set<String>> converted = new HashMap<>();
            for (Map.Entry<HttpMethod, Set<HttpMethod>> entry : methodMapping.entrySet()) {
                final Set<HttpMethod> overriddenHttpMethods = entry.getValue();
                if (overriddenHttpMethods != null && !overriddenHttpMethods.isEmpty()) {
                    Set<String> convertedOverridings = new HashSet<>(overriddenHttpMethods.size());
                    for (HttpMethod method : overriddenHttpMethods) {
                        convertedOverridings.add(method.name());
                    }
                    converted.put(entry.getKey().name(), convertedOverridings);
                }
            }
            if (!converted.isEmpty())
                return converted;
        }
        return null;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String overriddenMethod = request.getHeader(headerName);
        if (shouldApplyMethodOverriding(overriddenMethod, request)) {
            filterChain.doFilter(new HttpMethodRequestWrapper(request, overriddenMethod), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean shouldApplyMethodOverriding(String overriddenMethod, HttpServletRequest request) {
        if (methodMapping != null) {
            if (!ObjectUtils.isEmpty(overriddenMethod)) {
                final String requestMethod = request.getMethod();
                final Set<String> allowedOverridingMethod = methodMapping.get(requestMethod);
                if (allowedOverridingMethod != null) {
                    if (allowedOverridingMethod.contains(overriddenMethod)) {
                        logger.debug("Valid overridden http-request-method found for this request {}. Method id '{}'",
                                request.getRequestURI(), overriddenMethod);
                        return true;
                    }
                    logger.debug("Invalid overridden http-request-method detected for request {} {}."+
                                    "Method id '{}' but should be one of this '{}'", requestMethod,
                            request.getRequestURI(), overriddenMethod, allowedOverridingMethod);

                } else if (logger.isDebugEnabled()) {
                    logger.debug("No overridden http-request-method allowed for request {} {}. But found: {} ",
                            requestMethod, request.getRequestURI(), overriddenMethod);
                }
            } else if (logger.isDebugEnabled()) {
                logger.debug("No overridden http-request-method found for request {} {}", request.getMethod(),
                        request.getRequestURI());
            }
        }
        return false;
    }

    private static class HttpMethodRequestWrapper extends HttpServletRequestWrapper {

        private final String method;

        HttpMethodRequestWrapper(HttpServletRequest request, String method) {
            super(request);
            this.method = method;
        }

        public String getMethod() {
            return this.method;
        }
    }
}
