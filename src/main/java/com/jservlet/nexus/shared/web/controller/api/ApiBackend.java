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
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Rest control ApiBackend, replicate all HttpRequests to the Backend Server.
 * Provides a full support for MultiPart HttpRequests and Parameters inside a form with Content-Type: multipart/form-data.
 * <p>
 * All HttpRequests methods and protocols: Get, Post, Post Multipart File, Put, Put Multipart File, Patch, Patch Multipart File, Delete
 * <p>
 * Activated by only 'nexus.api.backend.enabled=true' in the configuration
 */
@RestController
@RequestMapping(value = "/api")
@ConditionalOnProperty(value = "nexus.api.backend.enabled")
@Hidden
public class ApiBackend extends ApiBase {

    private static final Logger logger = LoggerFactory.getLogger(ApiBackend.class);

    private static final String SOURCE = "REST-API-NEXUS-BACKEND";

    public ApiBackend() { super(SOURCE); }

    private BackendService backendService;

    @Autowired
    public final void setBackendService(BackendService backendService) {
        this.backendService = backendService;
    }

    @RequestMapping(value = "/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public final Object requestEntity(@RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request)
            throws NexusHttpException, NexusIllegalUrlException {
        // MultiValueMap store the MultiPartFiles and the Parameters Map
        MultiValueMap<String, Object> map = null;
        try {
            // The path within the handler mapping and its query
            String url = ((String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).replaceAll("api/", "");
            if (request.getQueryString() != null) url = url + "?" + request.getQueryString();

            // Get the MultipartRequest from the miscellaneous Web utilities NativeRequest!
            MultipartRequest multipartRequest = WebUtils.getNativeRequest(request, MultipartRequest.class);
            map = processMapResources(multipartRequest, request.getParameterMap());

            // Optimize logs writing, log methods can take time!
            if (logger.isDebugEnabled()) {
                logger.debug("Requested Url: {} '{}' args: {}, form: {}, body: {}, files: {}",
                        method, url, printQueryString(request.getQueryString()), printParameterMap(request.getParameterMap()), body, map.entrySet());
            }

            // Create a ResponseType!
            ResponseType<?> responseType = backendService.createResponseType(Object.class);
            Object obj = backendService.doRequest(url, method, responseType, !map.isEmpty() ? map : body, getAllHeaders(request));

            // Manage an EntityError!
            if (obj instanceof EntityError)
                return new ResponseEntity<>(((EntityError<?>) obj).getBody(), ((EntityError<?>) obj).getStatus());

            return obj;
        } catch (NexusResourceNotFoundException e) {
            // Return an error Message NOT_FOUND
            return super.getResponseEntity("404", "ERROR", e, HttpStatus.NOT_FOUND);
        } finally {
            // Clean all the Backend Resources inside the MultiValueMap
            if (map != null && !map.isEmpty()) cleanResources(map);
        }
    }

    /**
     * Get all Headers from the HttpServletRequest
     */
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

    /**
     * Print the parameterMap in a "Json" object style
     */
    private static String printParameterMap(Map<String, String[]> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, String[]>> it = map.entrySet().iterator();
        sb.append("{");
        while (it.hasNext()) {
            Map.Entry<String, String[]> entry = it.next();
            sb.append("\"").append(entry.getKey()).append("\"").append(":");
            Iterator<String> sIt = Arrays.asList(entry.getValue()).iterator();
            sb.append("[");
            while (sIt.hasNext()) {
                sb.append("\"").append(sIt.next()).append("\"");
                if (sIt.hasNext()) sb.append(",");
            }
            sb.append("]");
            if (it.hasNext()) sb.append(",");
        }
        return sb + "}";
    }

    /**
     * Print the raw queryString decoded
     */
    private static String printRawQueryString(String queryString) {
        return URLDecoder.decode(queryString, StandardCharsets.UTF_8);
    }

    /**
     * Print the queryString decoded
     */
    private static String printQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) return "{}";
        Map<String, String[]> map = new LinkedHashMap<>();
        StringTokenizer st = new StringTokenizer(queryString, "&");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            int idx = token.indexOf("=");
            if (idx != -1) {
                String key = idx > 0 ? token.substring(0, idx) : "";
                String value = (idx > 0 && token.length() > idx + 1) || (token.indexOf('=') == token.length()-1) || (token.indexOf('=') == 0)
                        ? token.substring(idx + 1) : token.substring(idx);
                String[] values;
                if (map.get(key) != null) {
                    values = appendInArray(map.get(key), URLDecoder.decode(value, StandardCharsets.UTF_8));
                } else {
                    if (!value.isEmpty()){
                        values = new String[] { URLDecoder.decode(value, StandardCharsets.UTF_8) };
                    } else {
                        values = new String[0];
                    }
                }
                if (!key.isEmpty()) map.put(key, values);
            } // none!
        }
        return printParameterMap(map);
    }

    /**
     * Append an element inside an Array
     */
    private static <T> T[] appendInArray(T[] array, T element) {
        final int len = array.length;
        array = Arrays.copyOf(array, len + 1);
        array[len] = element;
        return array;
    }

   /**
     * Prepare a LinkedMultiValueMap from a MultipartRequest, convert a MultipartFile to a Backend Resource.
     * And inject the parameterMap inside the LinkedMultiValueMap from a multipart HttpRequest.
     */
    private static MultiValueMap<String, Object> processMapResources(MultipartRequest multipartRequest, Map<String, String[]> mapParams) {
        MultiValueMap<String, Object> linkedMap = new LinkedMultiValueMap<>();
        if (multipartRequest != null) {
            MultiValueMap<String, MultipartFile> multiFileMap = multipartRequest.getMultiFileMap(); // MultiValue
            for (Map.Entry<String, List<MultipartFile>> entry : multiFileMap.entrySet()) {
                List<MultipartFile> files = entry.getValue();
                for (MultipartFile file : files) {
                    try {
                        Resource resource = new BackendResource(file);
                        linkedMap.add(entry.getKey(), resource);
                    } catch (IOException io) {
                        logger.error("Multipart to Resource file: '{}' Error: {}", file.getOriginalFilename(), io.getMessage());
                    }
                }
            }
            // Inject all the parameterMap now!
            for (String key : mapParams.keySet()) {
                linkedMap.addAll(key, Arrays.asList(mapParams.get(key))); // MultiValue
            }
        }
        return linkedMap;
    }

    /**
     * Clean all the BackendResource already sent to the BackendService
     */
    private void cleanResources(MultiValueMap<String, Object> map) {
        for (Map.Entry<String, List<Object>> entry : map.entrySet()) {
            List<Object> objects = entry.getValue(); // MultiValue
            for (Object obj : objects) {
                if (obj instanceof BackendResource) {
                    BackendResource resource = (BackendResource) obj;
                    if (resource.getFile().delete()) {
                        if (logger.isDebugEnabled())
                            logger.debug("Resource deleted: '{}' file: '{}'", entry.getKey(), resource.getFilename());
                    } else {
                        logger.warn("Resource not deleted: '{}' file: '{}'", entry.getKey(), resource.getFilename());
                    }
                }
            }
        }
    }

    /**
     * Build a new BackendResource because the MultipartFile Resource will be deleted before the end of Request.
     * The BackendResource can convert a MultipartFile to a temporary Resource, ready to be sent!
     */
    static class BackendResource extends AbstractResource {

        private final File fileUpload;
        private final String originalFilename;

        public BackendResource(MultipartFile multipartFile) throws IOException {
            Assert.notNull(multipartFile, "MultipartFile must not be null");
            // Create a temporary file in java.io.tmpdir and delete on exit if it exists!
            fileUpload = File.createTempFile(System.currentTimeMillis() + "_nexus_", ".tmp");
            fileUpload.deleteOnExit();
            logger.debug("Create temp File: {}", fileUpload.getAbsolutePath());

            // Get original Filename
            originalFilename = multipartFile.getOriginalFilename();

            // Consume the input Stream and transfer it to a new local file
            multipartFile.transferTo(fileUpload);
        }

        /**
         * This implementation always returns {@code true}.
         */
        @Override
        public boolean exists() {
            return fileUpload.exists();
        }

        /**
         * This implementation always returns {@code false}.
         */
        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public long contentLength() {
            return this.fileUpload.length();
        }

        @Override
        public @NonNull URL getURL() throws IOException {
            return this.fileUpload.toPath().toUri().toURL();
        }

        @Override
        public @NonNull File getFile() {
            return this.fileUpload;
        }

        @Override
        public @NonNull boolean isFile() {
            return true;
        }

        @Override
        public String getFilename() {
            return this.originalFilename;
        }

        /**
         * This implementation throws IllegalStateException if attempting to
         * read the underlying stream multiple times.
         */
        @Override
        public @NonNull InputStream getInputStream() throws IOException, IllegalStateException {
            return new FileInputStream(fileUpload);
        }

        /**
         * This implementation returns a description that has the Multipart name.
         */
        @Override
        public @NonNull String getDescription() {
            return "BackendResource [" + this.originalFilename + "]";
        }

        @Override
        public boolean equals(@Nullable Object other) {
             return (this == other || (other instanceof BackendResource &&
                    this.fileUpload.equals(((BackendResource) other).fileUpload)));
        }

        @Override
        public int hashCode() {
            return this.fileUpload.hashCode();
        }
    }

}
