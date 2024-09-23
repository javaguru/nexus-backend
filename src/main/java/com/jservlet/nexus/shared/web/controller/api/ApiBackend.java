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

package com.jservlet.nexus.shared.web.controller.api;

import com.jservlet.nexus.shared.exceptions.NexusHttpException;
import com.jservlet.nexus.shared.exceptions.NexusIllegalUrlException;
import com.jservlet.nexus.shared.exceptions.NexusResourceNotFoundException;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendService.ResponseType;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.EntityError;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Rest control ApiBackend, replicate all HTTP requests to the Backend Server...
 * <p>
 * Activated by only 'nexus.api.backend.enabled=true' in the configuration
 */
@RestController
@RequestMapping(value = "/api")
@ConditionalOnProperty(value = "nexus.api.backend.enabled")
@Hidden
public class ApiBackend extends ApiBase {

    private static final Logger logger = LoggerFactory.getLogger(ApiBackend.class);

    private static final String SOURCE = "NEXUS-REST-API-BACKEND";

    public ApiBackend() { super(SOURCE); }

    private BackendService backendService;

    @Autowired
    public final void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    @RequestMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object requestEntity(@RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request)
            throws NexusHttpException, NexusIllegalUrlException {
        try {
            // The path within the handler mapping and its query
            String url = ((String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).replaceAll("api/", "");
            if (request.getQueryString() != null) url = url + "?" + request.getQueryString();
            logger.debug("Requested Url : {} {}", method, url);
            // Create a ResponseType!
            ResponseType<?> responseType = backendService.createResponseType(Object.class);
            Object obj = backendService.doRequest(url, method, responseType, body, getAllHeaders(request), request.getParameterMap());
            // Manage an EntityError!
            if (obj instanceof EntityError)
                return new ResponseEntity<>(((EntityError<?>) obj).getBody(), ((EntityError<?>) obj).getStatus());
            return obj;
        } catch (NexusResourceNotFoundException e) {
            // Re-encapsulate Not Found Exception in a ResponseEntity!
            return new ResponseEntity<>(super.getResponseEntity(e.getMessage(), HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND);
        }
    }

    private static HttpHeaders getAllHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(request.getRequestURL().toString());
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.add(headerName, request.getHeader(headerName));
        }
        return headers;
    }

}
