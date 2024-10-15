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
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.EntityBackend;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.servlet.HandlerMapping;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Rest control ApiBackend, replicate all HttpRequests to the Backend Server. <br>
 * Provides a full support for MultiPart HttpRequests and Parameters inside a form with
 * Content-Type: multipart/form-data.
 * <p>
 *     All HttpRequests methods: Get, Post, Post Multipart File, Put, Put Multipart File,
 *     Patch, Patch Multipart File, Delete
 * <br>
 * Full support Request Json Entity Object: application/json, application/x-www-form-urlencoded <br>
 * Full support MultipartRequest Resources and Map parameters, embedded form Json Entity Object: multipart/form-data <br>
 * Full support Response in Json Entity Object: application/json <br>
 * Full support Response in ByteArray Resource file: application/octet-stream <br>
 * Full support Streaming Http Response Json Entity Object: application/octet-stream, accept header Range bytes
 * <p>
 *     ApiBackend ResponseType is now a Resource ByteArray by default (see settings.properties). All is Bytes!
 *     <br>
 *     The ResourceMatchers Config can be configured on specific ByteArray Resources path <br>
 *     and on specific methods GET, POST, PUT, PATCH and Ant paths pattern: <br>
 *      nexus.backend.api-backend-resource.matchers.matchers1.method=GET <br>
 *      nexus.backend.api-backend-resource.matchers.matchers1.pattern=/api/encoding/** <br>
 *      nexus.backend.api-backend-resource.matchers.matchers2.method=GET <br>
 *      nexus.backend.api-backend-resource.matchers.matchers2.pattern=/api/decoding/** <br>
 *      etc...
 * <p>
 * <p>
 *      The Http Responses can be considerate as Resources, the Http header "Accept-Ranges: bytes" is injected and allow you to use
 *      the Http header 'Range:bytes=-1000' in the request and by example grabbed the last 1000 bytes (or a range of Bytes). <br>
 *      And the Http Responses will come back without a "Transfer-Encoding: chunked" HttpHeader cause now the header Content-Length.
 *      <br><br>
 *      For configure all the Responses in Resource put eh Method empty and use the path pattern=/api/** <br>
 *      nexus.backend.api-backend-resource.matchers.matchers1.method= <br>
 *      nexus.backend.api-backend-resource.matchers.matchers1.pattern=/api/** <br>
 *      <br><br>
 *      For remove the Http header "Transfer-Encoding: chunked" the header Content-Length need to be calculated,
 *      enable the ShallowEtagHeader Filter in the configuration for force to calculate the header Content-Length
 *      for all the Response Json Entity Object.
 * </p>
 * Activated by the key <b>'nexus.api.backend.enabled=true'</b> in the Configuration properties.
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

    private ResourceMatchersConfig matchersConfig;

    private OrRequestMatcher orRequestMatcher;

    @Autowired
    public final void setBackendService(BackendService backendService, ResourceMatchersConfig matchersConfig) {
        this.backendService = backendService;
        this.matchersConfig = matchersConfig;
    }

    /**
     * Prepare matchers Methods and Ant paths pattern dedicated only for the Resources
     */
    @PostConstruct
    private void postConstruct() {
        List<RequestMatcher> requestMatchers = new ArrayList<>();
        Map<String, ResourceMatchersConfig.Matcher> map = matchersConfig.getMatchers();
        for (Map.Entry<String, ResourceMatchersConfig.Matcher> entry : map.entrySet()) {
            requestMatchers.add(new AntPathRequestMatcher(entry.getValue().getPattern(), entry.getValue().getMethod()));
            logger.info("Config ResourceMatchers: {} '{}'", entry.getValue().getMethod(), entry.getValue().getPattern());
        }
        // Mandatory, not an empty RequestMatcher!
        if (requestMatchers.isEmpty()) {
            requestMatchers.add(new AntPathRequestMatcher("*/**", null));
            logger.warn("Config ResourceMatchers: No ByteArray Resource specified!");
        }
        orRequestMatcher = new OrRequestMatcher(requestMatchers);
    }

    /**
     * Inner ConfigurationProperties keys prefixed with 'nexus.backend.api-backend-resource' and
     * Lopping on incremental keys 'matchers.{name}[X].method' and 'matchers.{name}[X].pattern'
     */
    @ConfigurationProperties("nexus.backend.api-backend-resource")
    public static class ResourceMatchersConfig {
        private final Map<String, Matcher> matchers = new HashMap<>();

        public Map<String, Matcher> getMatchers() {
            return matchers;
        }

        public static class Matcher {
            private String method;
            private String pattern;

            public String getMethod() {
                return method;
            }

            public void setMethod(String method) {
                this.method = method;
            }

            public String getPattern() {
                return pattern;
            }

            public void setPattern(String pattern) {
                this.pattern = pattern;
            }
        }
    }

    /**
     * Manage a Request Json Entity Object and a Request Map parameters. <br>
     * Or a MultipartRequest encapsulated a List of BackendResource and a Request Map parameters, and form Json Entity Object <br>
     * And return a ResponseEntity Json Entity Object or a ByteArray Resource file or any others content in ByteArray...<br>
     * <br>
     * For a @RequestMapping allow headers is set to GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS
     *
     * @param body                          String representing the RequestBody Object, just transfer the RequestBody
     * @param method                        HttpMethod GET, POST, PUT, PATCH or DELETE
     * @param request                       The current HttpServletRequest
     * @param nativeWebRequest              The current NativeWebRequest for get the MultipartRequest
     * @return Object                       Return a ResponseEntity Object or a ByteArray Resource
     * @throws NexusHttpException           Exception when a http request to the backend fails
     * @throws NexusIllegalUrlException     Exception when an illegal url will be requested
     */
    @RequestMapping(value = "/**")
    public final Object requestEntity(@RequestBody(required = false) String body, HttpMethod method,
                                      HttpServletRequest request, NativeWebRequest nativeWebRequest)
            throws NexusHttpException, NexusIllegalUrlException {
        // MultiValueMap store the MultiPartFiles and the Parameters Map
        MultiValueMap<String, Object> map = null;
        try {
            // Any path within handler mapping without "api/" and with its query
            String url = ((String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE)).replaceAll("api/", "");
            if (request.getQueryString() != null) url = url + "?" + request.getQueryString();

            // Get the MultipartRequest from the NativeRequest!
            MultipartRequest multipartRequest = nativeWebRequest.getNativeRequest(MultipartRequest.class);
            map = processMapResources(multipartRequest, request.getParameterMap());

            // Optimize logs writing, log methods can take time!
            if (logger.isDebugEnabled()) {
                logger.debug("Requested Url: {} '{}' args: {}, form: {}, body: {}, files: {}",
                        method, url, printQueryString(request.getQueryString()), printParameterMap(request.getParameterMap()), body, map.entrySet());
            }

            // Create a ResponseType Object or Resource by RequestMatcher
            ResponseType<?> responseType;
            if (!orRequestMatcher.matches(request)) {
                responseType = backendService.createResponseType(Object.class);
            } else {
                responseType = backendService.createResponseType(Resource.class);
            }

            // Return a EntityError or a EntityBackend
            Object obj = backendService.doRequest(url, method, responseType, !map.isEmpty() ? map : body, getAllHeaders(request));

            // Manage a Generics EntityError embedded a Json Entity Object!
            if (obj instanceof EntityError) {
                EntityError<?> entityError = (EntityError<?>) obj;
                final HttpHeaders newHeaders = getBackendHeaders(entityError.getHttpHeaders());
                return new ResponseEntity<>(entityError, newHeaders, entityError.getStatus());
            }

            // Manage a Generics EntityBackend embedded a Json Entity Object or a Resource!
            EntityBackend<?> entityBackend = (EntityBackend<?>) obj;
            final HttpHeaders newHeaders = getBackendHeaders(entityBackend.getHttpHeaders());
            if (entityBackend.getBody() instanceof Resource) {
                Resource resource = (Resource) entityBackend.getBody();
                return new ResponseEntity<>(resource, newHeaders, entityBackend.getStatus());
            } else {
                return new ResponseEntity<>(entityBackend.getBody(), newHeaders, entityBackend.getStatus());
            }
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
     * Default List of Headers transfer
     */
    private final static List<String> TRANSFER_HEADERS =
            List.of(HttpHeaders.SERVER,
                    HttpHeaders.SET_COOKIE,
                    HttpHeaders.ETAG,
                    HttpHeaders.DATE, // transfer as Date-Backend
                    HttpHeaders.USER_AGENT,
                    "test" // Test postman-echo
            );

    /**
     * Transfer some headers from the Backend RestOperations.
     * Not CONTENT_LENGTH, CONTENT_RANGE or TRANSFER_ENCODING. Cause already sent in their own Context.
     * Case SET_COOKIE need a Store!
     */
    private static HttpHeaders getBackendHeaders(HttpHeaders readHeaders) {
        HttpHeaders newHeaders = new HttpHeaders();
        if (readHeaders == null || readHeaders.isEmpty()) return newHeaders;
        // Original CONTENT_TYPE for a Resource and its charset if it exists
        if (readHeaders.getFirst(HttpHeaders.CONTENT_TYPE) != null) {
            newHeaders.set(HttpHeaders.CONTENT_TYPE, readHeaders.getFirst(HttpHeaders.CONTENT_TYPE));
        } else {// ByteArray by default!
            newHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        }
        for (String headerName : TRANSFER_HEADERS) {
            if (readHeaders.getFirst(headerName) != null) {
                if (HttpHeaders.DATE.equals(headerName)) {
                    newHeaders.add(HttpHeaders.DATE + "-Backend", readHeaders.getFirst(HttpHeaders.DATE));
                } else {
                    newHeaders.add(headerName, readHeaders.getFirst(headerName));
                }
            }
        }
        return newHeaders;
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
    private static class BackendResource extends AbstractResource {

        private final File fileUpload;
        private final String originalFilename;

        public BackendResource(MultipartFile multipartFile) throws IOException {
            Assert.notNull(multipartFile, "MultipartFile must not be null");
            // Create a temporary file in java.io.tmpdir
            fileUpload = File.createTempFile(System.currentTimeMillis() + "_nexus_", ".tmp");

            // Get original Filename
            originalFilename = multipartFile.getOriginalFilename();

            // Consume the input Stream and transfer it to a new local file
            multipartFile.transferTo(fileUpload);

            logger.debug("BackendResource created: {}", fileUpload.getAbsolutePath());
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
