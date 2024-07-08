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
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.*;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Rest control ApiBackend, redirects all HTTP requests to the Backend Server...
 * <p>
 * Activated by only 'nexus.api.backend.enabled=true' in the configuration
 */
@RestController
@RequestMapping("/api")
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

    @GetMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object get(HttpMethod method, HttpServletRequest request)
            throws NexusIllegalUrlException, NexusHttpException {
        return getResponseEntity(Object.class,null, method, request);
    }

    @PostMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object post(@RequestBody(required = false) Object body, HttpMethod method, HttpServletRequest request)
            throws NexusIllegalUrlException, NexusHttpException {
        return getResponseEntity(Object.class, body, method, request);
    }

    @PutMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object put(@RequestBody(required = false) Object body, HttpMethod method, HttpServletRequest request)
            throws NexusIllegalUrlException, NexusHttpException {
        return getResponseEntity(Object.class, body, method, request);
    }

    @DeleteMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object delete(HttpMethod method, HttpServletRequest request)
            throws NexusIllegalUrlException, NexusHttpException {
        return getResponseEntity(HttpStatus.class, null, method, request);
    }

    @PatchMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object patch(@RequestBody(required = false) Object body, HttpMethod method, HttpServletRequest request)
            throws NexusIllegalUrlException, NexusHttpException {
        return getResponseEntity(HttpStatus.class, body, method, request);
    }

    private Object getResponseEntity(Class<?> objectClass, Object body, HttpMethod method, HttpServletRequest request)
            throws NexusHttpException, NexusIllegalUrlException {
        try {
            // create a ResponseType!
            ResponseType<?> responseType = backendService.createResponseType(objectClass);
            Object obj = backendService.doRequest(getUrl(request), method, responseType, body, getAllHeaders(request));
            // Manage an EntityError!
            if (obj instanceof EntityError)
                return new ResponseEntity<>(((EntityError<?>) obj).getBody(), ((EntityError<?>) obj).getStatus());
            return obj;
        } catch (NexusResourceNotFoundException e) {
            // Re-encapsulate Not Found Exception in a ResponseEntity!
            return new ResponseEntity<>(super.getResponseEntity(e.getMessage(), HttpStatus.NOT_FOUND), HttpStatus.NOT_FOUND);
        }
    }

    private static String getUrl(HttpServletRequest request) {
        String url = ((String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).replaceAll("api/", "");
        Map<String, String[]> parameterMap = request.getParameterMap();
        List<String> params = new ArrayList<>();
        parameterMap.forEach((key, value) -> params.add(key + "=" +
                String.join("&", Arrays.stream(value).distinct().toArray(String[]::new)))); // remove duplicate values!
        url = !parameterMap.isEmpty() ? url + "?" + String.join("&", params) : url;
        logger.debug("Request for: {}", url);
        return UriComponentsBuilder.fromUriString(url).buildAndExpand().toString(); // No .encode()!
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
