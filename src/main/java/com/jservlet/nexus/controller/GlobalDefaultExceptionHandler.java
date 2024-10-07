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

package com.jservlet.nexus.controller;

import com.jservlet.nexus.shared.web.controller.ApiBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.RestClientException;

import javax.servlet.http.HttpServletRequest;

import java.util.Objects;

import static org.springframework.http.HttpStatus.*;

/**
 * Global DefaultExceptionHandler
 */
@ControllerAdvice
public class GlobalDefaultExceptionHandler extends ApiBase {

    private static final Logger logger = LoggerFactory.getLogger(GlobalDefaultExceptionHandler.class);

    private static final String GLOBAL_SOURCE = "GLOBAL-ERROR-NEXUS-BACKEND";

    public GlobalDefaultExceptionHandler() {
        super(GLOBAL_SOURCE);
    }

    @ExceptionHandler(value = { RestClientException.class })
    public ResponseEntity<?> handleResourceAccessException(HttpServletRequest request, RestClientException e) {
        logger.error("Intercepted RestClientException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                e.getMessage(), request.getRemoteHost(), request.getMethod(), request.getServletPath(), request.getHeader("User-Agent"));
        // ByeArray Resource request could not be completed due to a conflict with the current state of the target resource!
        if (Objects.requireNonNull(e.getMessage()).startsWith("Error while extracting response for type [class java.lang.Object] and content type")) {
            String msg = "No ResourceMatchers configured. Open the Method '" + request.getMethod() + "' and the Uri '" + request.getServletPath() + "' as ByteArray Resource!";
            return super.getResponseEntity("409", "ERROR", msg, CONFLICT);
        }
        return super.getResponseEntity("503", "ERROR", "Remote Service Unavailable: " + e.getMessage(), SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(value = { HttpMessageNotReadableException.class })
    public ResponseEntity<?> handleNotReadableException(HttpServletRequest request, HttpMessageNotReadableException e) {
        logger.error("Intercepted HttpMessageNotReadableException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                e.getMessage(), request.getRemoteHost(), request.getMethod(), request.getServletPath(), request.getHeader("User-Agent"));
        return super.getResponseEntity(METHOD_NOT_ALLOWED);// 405 or 406 NOT_ACCEPTABLE !?
    }

    @ExceptionHandler(value = { RequestRejectedException.class })
    public ResponseEntity<?> handleRejectedException(HttpServletRequest request, RequestRejectedException e) {
        // Log security, the WAFFilter return a request not readable anymore
        logger.error("Intercepted RequestRejectedException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                e.getMessage(), request.getRemoteHost(), request.getMethod(), request.getServletPath(), request.getHeader("User-Agent"));
        return super.getResponseEntity("400", "ERROR", "Request rejected!", BAD_REQUEST);
    }

    /*
     *  Any other's error
     */
    @ExceptionHandler(value = { Exception.class })
    public ResponseEntity<?> handleResourceAccessException(HttpServletRequest request, Exception e) {
        logger.error("Intercepted Exception: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                e.getMessage(), request.getRemoteHost(), request.getMethod(), request.getServletPath(), request.getHeader("User-Agent"));
        return getResponseEntity("500",  e);
    }
}
