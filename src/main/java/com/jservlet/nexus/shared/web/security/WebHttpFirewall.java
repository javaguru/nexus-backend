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

package com.jservlet.nexus.shared.web.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.StrictHttpFirewall;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * WebHttpFirewall overrides the StrictHttpFirewall to log some useful information about blocked requests
 * throw a RequestRejectedException
 * <br><br>
 * The Spring StrictHttpFirewall rejected by default:<br>
 * - Forbidden HttpMethod<br>
 * - Block listed Urls<br>
 * - Untrusted Hosts<br>
 * - No Normalized Request<br>
 * - None Printable Ascii Characters In FieldName<br>
 * <br><br>
 * And the StrictHttpFirewall can accept a list Predicates to parse the request Headers (include Cookie) and
 * Key/Value Parameter Map.
 * <br><br>
 * For parsing a Json RequestBody see the class com.webfg.service.waf.WAFFilter
 */
public final class WebHttpFirewall extends StrictHttpFirewall {

    private static final Logger logger = LoggerFactory.getLogger(WebHttpFirewall.class);

    public WebHttpFirewall() {
        super();
        logger.info("Starting WebHttpFirewall");
    }

    /**
     * Provides the request object which will be passed through the filter chain.
     *
     * @param request The original HttpServletRequest.
     * @throws RequestRejectedException if the request should be rejected immediately.
     * @return FirewalledRequest (required by the HttpFirewall interface) which
     * inconveniently breaks the general contract of ServletFilter because
     * we can't upcast this to an HttpServletRequest. This prevents us
     * from re-wrapping this using an HttpServletRequestWrapper.
     */
    @Override
    public FirewalledRequest getFirewalledRequest(final HttpServletRequest request) throws RequestRejectedException {
        try {
            return super.getFirewalledRequest(request);
        } catch (RequestRejectedException ex) {
           logger.error("Intercepted RequestRejectedException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                    ex.getMessage(), request.getRemoteHost(), request.getMethod(),
                   request.getServletPath(), request.getHeader("User-Agent"));

            // Wrap in a new RequestRejectedException with request metadata and a shallower stack trace.
            throw new RequestRejectedException(ex.getMessage()) {
                @Override
                public synchronized Throwable fillInStackTrace() {
                    return this; // suppress the stack trace.
                }
            };
        }
    }

    /**
     * Provides the response which will be passed through the filter chain.
     * This method isn't extensible because the request may already be committed.
     * Furthermore, this is only invoked for requests that were not blocked, so we can't
     * control the status or response for blocked requests here.
     *
     * @param response The original HttpServletResponse.
     * @return HttpServletResponse the original response or a replacement/wrapper.
     */
    @Override
    public HttpServletResponse getFirewalledResponse(final HttpServletResponse response) {
        return super.getFirewalledResponse(response);
    }
}
