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

package com.jservlet.nexus.shared.web.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * WebListener create a fingerprint for each request.
 * The fingerprint can be inserted in access- and error-logs and its a help for tracking and debugging.
 * <p>
 * If you want to track additional info overwrite the template-method addAdditionalInfo..
 *
 * Activated WebListener by only 'nexus.api.backend.listener.requestid.enabled=true' in the configuration
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.listener.requestid.enabled")
public class RequestIdServletRequestListener implements ServletRequestListener {

    private static final Logger logger = LoggerFactory.getLogger(RequestIdServletRequestListener.class);

    private static final String REQUEST_ID_HEADER = "APP-REQUEST-ID";
    private static final String FORWARDED_HEADER = "FD_REMOTE_ADDR";

    public RequestIdServletRequestListener() {
        super();
        logger.info("Started RequestId Servlet Listener");
    }
    @Override
    public void requestDestroyed(ServletRequestEvent servletRequestEvent) {
        final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
        if (servletRequest instanceof HttpServletRequest) {
            MDC.remove(REQUEST_ID_HEADER);
        }
    }

    @Override
    public void requestInitialized(ServletRequestEvent servletRequestEvent) {
        final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            final String fingerPrint = generateAndSetFingerPrint(request);
            MDC.put(REQUEST_ID_HEADER, fingerPrint);
        }
    }

    private String generateAndSetFingerPrint(HttpServletRequest request) {
        String IP;
        if (request.getHeader(FORWARDED_HEADER) != null) {
            IP = request.getHeader(FORWARDED_HEADER);
        } else {
            IP = request.getRemoteAddr();
        }
        final String md5Digest = getMd5Digest(IP + "-" + System.currentTimeMillis());
        request.setAttribute(REQUEST_ID_HEADER, md5Digest);
        return md5Digest;
    }

    private String getMd5Digest(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger(1, messageDigest);
            return number.toString(16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
