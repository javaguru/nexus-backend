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

package com.jservlet.nexus.shared.service.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.exceptions.*;
import com.jservlet.nexus.shared.service.backend.api.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This implementation Rest BackendService is used for the communication to a backend.
 * It provides methods for all supported http protocols on the backend side.
 * Normally it communicate to an api interface backend.
 * <p>
 * The backend will be configured by the following properties
 * <p>
 * BackendServiceImpl#setBackendURL
 * <ul>
 *     <li>nexus.backend.url: set the URL to access to the backend</li>
 * </ul>
 */
@Service
public final class BackendServiceImpl implements BackendService {

    private static final Logger logger = LoggerFactory.getLogger(BackendServiceImpl.class);

    private static final ResponseType<Void> EMPTY_RESPONSE = new ResponseTypeImpl<>();

    private String backendURL;

    private RestOperations restOperations;

    private ObjectMapper objectMapper;

    /**
     * Return by the default a Json Entity Object or Resource, else if true a Generics Object.
     */
    private boolean isHandleBackendEntity = false;

    public BackendServiceImpl() {
    }

    public BackendServiceImpl(boolean isHandleBackendEntity) {
        this.isHandleBackendEntity = isHandleBackendEntity;
    }

    public void setBackendURL(String backendURL) {
        this.backendURL = backendURL;
    }

    public void setRestOperations(RestOperations restOperations) {
        this.restOperations = restOperations;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Value("${nexus.backend.header.authorization.username:}")
    private String username;

    @Value("${nexus.backend.header.authorization.password:}")
    private String password;

    @Value("${nexus.backend.header.cookie:}")
    private String cookie;

    @Value("${nexus.backend.header.bearer:}")
    private String bearer;

    @Value("${nexus.backend.header.remove:false}")
    private boolean removeHeaders;

    @Value("${nexus.backend.header.host.remove:false}")
    private boolean removeHostHeader;
    @Value("${nexus.backend.header.origin.remove:false}")
    private boolean removeOriginHeader;

    @Value("${nexus.backend.http.response.truncated:false}")
    private boolean truncated;
    @Value("${nexus.backend.http.response.truncated.maxLength:1000}")
    private int maxLengthTruncated;

    @Value("${nexus.backend.header.user-agent:JavaNexus}")
    private String userAgent = "JavaNexus";


    @Override
    public <T> T get(String url, ResponseType<T> responseType)
            throws NexusGetException, NexusResourceNotFoundException {
        try {
            return doRequest(url, HttpMethod.GET, responseType, null, null);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to GET entity/entities on backend by url '{}'", url);
            throw new NexusGetException("An error occurred while GET entity/entities", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to GET the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    @Override
    public Resource getFile(String url) throws NexusGetException, NexusResourceNotFoundException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
            return doRequest(url, HttpMethod.GET, createResponseType(Resource.class), null, headers);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to GET File entity/entities on backend by url '{}'", url);
            throw new NexusGetException("An error occurred while GET an entity", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to GET File the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    @Override
    public <T> T post(String url, Object data, ResponseType<T> responseType)
            throws NexusCreationException, NexusResourceExistsException {
        try {
            return doRequest(url, HttpMethod.POST, responseType, data, null);
        } catch (NexusResourceExistsException e) {
            throw e;
        } catch (NexusHttpException | NexusIllegalUrlException | NexusResourceNotFoundException | HttpStatusCodeException e) {
            logger.error("Failed to POST entity/entities on backend by url '{}'", url);
            throw new NexusCreationException("An error occurred while POST an entity", e);
        }
    }

    @Override
    public <T> T postFile(String url, Resource resource, ResponseType<T> responseType)
            throws NexusCreationException, NexusResourceExistsException {
        try {
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            map.add("file", resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.add(HttpHeaders.USER_AGENT, userAgent);
            return doRequest(url, HttpMethod.POST, responseType, map, headers);
        } catch (NexusResourceExistsException e) {
            throw e;
        } catch (NexusHttpException | NexusIllegalUrlException | NexusResourceNotFoundException | HttpStatusCodeException e) {
            logger.error("Failed to POST File entity/entities on backend by url '{}'", url);
            throw new NexusCreationException("An error occurred while POST an entity", e);
        }
    }

    @Override
    public <T> T put(String url, Object data, ResponseType<T> responseType)
            throws NexusModificationException, NexusResourceExistsException, NexusResourceNotFoundException {
        try {
            return doRequest(url, HttpMethod.PUT, responseType, data, null);
        } catch (NexusResourceExistsException e) {
            logger.error("Failed to PUT the entity on backend due to conflict (url '{}')", url);
            throw e;
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to PUT entity/entities on backend by url '{}'", url);
            throw new NexusModificationException("An error occurred while PUT an entity", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to PUT the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    @Override
    public <T> T putFile(String url, Resource resource, ResponseType<T> responseType)
            throws NexusResourceNotFoundException, NexusModificationException, NexusResourceExistsException {
        try {
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            map.add("file", resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.add(HttpHeaders.USER_AGENT, userAgent);
            return doRequest(url, HttpMethod.PUT, responseType, map, headers);
        } catch (NexusResourceExistsException e) {
            throw e;
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to PUT File entity/entities on backend by url '{}'", url);
            throw new NexusModificationException("An error occurred while PUT an entity", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to PUT File the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    @Override
    public void delete(String url) throws NexusDeleteException, NexusResourceNotFoundException {
        try {
            doRequest(url, HttpMethod.DELETE, EMPTY_RESPONSE, null, null);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to DELETE entity/entities on backend by url '{}'", url);
            throw new NexusDeleteException("An error occurred while DELETE entity/entities", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to DELETE the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    @Override
    public <T> T patch(String url, Object data, ResponseType<T> responseType)
            throws NexusModificationException, NexusResourceExistsException, NexusResourceNotFoundException {
        try {
            return doRequest(url, HttpMethod.PATCH, responseType, data, null);
        } catch (NexusResourceExistsException e) {
            logger.error("Failed to PATCH the entity on backend due to conflict (url '{}')", url);
            throw e;
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to PATCH entity/entities on backend by url '{}'", url);
            throw new NexusModificationException("An error occurred while PATCH an entity", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to PATCH the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    @Override
    public <T> T patchFile(String url, Resource resource, ResponseType<T> responseType)
            throws NexusResourceNotFoundException, NexusModificationException, NexusResourceExistsException {
        try {
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            map.add("file", resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.add(HttpHeaders.USER_AGENT, userAgent);
            return doRequest(url, HttpMethod.PATCH, responseType, map, headers);
        } catch (NexusResourceExistsException e) {
            throw e;
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            logger.error("Failed to PATCH File entity/entities on backend by url '{}'", url);
            throw new NexusModificationException("An error occurred while PATCH an entity", e);
        } catch (NexusResourceNotFoundException e) {
            logger.error("Failed to PATCH File the entity on backend because the resource couldn't be found {}", url);
            throw e;
        }
    }

    public <T> T doRequest(String url, HttpMethod method, ResponseType<T> responseType, Object body, HttpHeaders headers)
            throws NexusResourceNotFoundException, NexusHttpException, NexusIllegalUrlException {
        try {
            ParameterizedTypeReference<T> typeReference = responseType.getResponseParameterizedTypeReference();
            if (typeReference != null) {
                return handleResponse(restOperations.exchange(getBackendURL(url), method, createRequestEntity(body, headers), typeReference));
            } else {
                Class<T> responseClass = responseType.getResponseClass();
                return handleResponse(restOperations.exchange(getBackendURL(url), method, createRequestEntity(body, headers), responseClass));
            }
        } catch (HttpStatusCodeException e) {
            // WARN RestClientResponseException use now the default Charset UTF-8 vs ISO_8859_1 in Spring < 5.1.18
            if (isHandleHttpStatus(e.getStatusCode())) {
                // Test the ResponseHeaders and the Content-Type
                HttpHeaders responseHeaders = e.getResponseHeaders();
                if (responseHeaders == null) responseHeaders = new HttpHeaders();
                if (responseHeaders.getContentType() == null) responseHeaders.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                return handleResponse(new ResponseEntity<>(
                        (T) e.getResponseBodyAsByteArray(), e.getResponseHeaders(), e.getStatusCode()));
            }
            // handle the default ErrorMessage or an Exception...
            return handleResponseError(url, e);
        }
    }

    private final static EnumSet<HttpStatus> listHttpStatusError = EnumSet.of(
            HttpStatus.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED,
            HttpStatus.METHOD_NOT_ALLOWED,
            HttpStatus.INTERNAL_SERVER_ERROR);

    /**
     * Returns true if a specific HttpStatus is contained in th list
     *
     * @param status Ht tpStatus
     * @return true Returns true if HttpStatus has to be handled
     */
    private boolean isHandleHttpStatus(HttpStatus status) {
        return isHandleBackendEntity && listHttpStatusError.contains(status);
    }

    /**
     * Returns the final request url to connect to the backend.
     * The url will be composed with the backend context and the specified <code>url</code>.
     *
     * @param url The url to finalize
     * @return The finalized url to connect to the backend
     * @throws NexusIllegalUrlException When the <code>url</code> is empty
     */
    private String getBackendURL(String url) throws NexusIllegalUrlException {
        if (ObjectUtils.isEmpty(url)) throw new NexusIllegalUrlException("The parameter 'url' should not be empty!");
        if (!url.startsWith("/")) url = "/" + url;
        logger.debug("BackendURL: {}", backendURL + url);
        return backendURL + url;
    }

    @SuppressWarnings("unchecked")
    private <T> T handleResponse(ResponseEntity<T> exchange) {
        T responseBody = exchange.getBody();
        HttpStatus httpStatus = exchange.getStatusCode();
        HttpHeaders httpHeaders = exchange.getHeaders();
        if (logger.isDebugEnabled()) logger(httpHeaders, responseBody, httpStatus, maxLengthTruncated, truncated);
        if (responseBody == null) return (T) httpStatus;
        if (isHandleBackendEntity || isHandleHttpStatus(httpStatus)) return (T) new EntityBackend<>(responseBody, httpHeaders, httpStatus);
        return responseBody;
    }

    private static <T> void logger(HttpHeaders httpHeaders, T responseBody, HttpStatus httpStatus, int maxLengthTruncated, boolean truncated) {
        logger.debug("Headers response: {}", httpHeaders);
        if (responseBody != null) {
            if (responseBody instanceof Resource) {
                Resource resource = (Resource) responseBody;
                String body = null;
                try {
                    body = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    logger.error("Resource not readable: {}", e.getMessage());
                } finally {
                    logger.debug("The response is: {} {} {}", httpStatus, resource.getDescription(), LogFormatUtils.formatValue(body, maxLengthTruncated, truncated));
                }
            } else {
                logger.debug("The response is: {} {}", httpStatus, LogFormatUtils.formatValue(responseBody, maxLengthTruncated, truncated));
            }
        } else {
            logger.debug("The response is empty with HttpState: {}", httpStatus);
        }
    }

    private <T> T handleResponseError(String url, HttpStatusCodeException e) throws NexusResourceNotFoundException, NexusHttpException {
        switch (e.getStatusCode()) {
            case NOT_FOUND: throw new NexusResourceNotFoundException("Failed to request to the backend. Resource not found. URI: " + url);
            case CONFLICT: throw new NexusResourceExistsException("Resource already exist. URI: " + url);
            case METHOD_NOT_ALLOWED:
            case UNAUTHORIZED:
            case INTERNAL_SERVER_ERROR:
                try {
                    // ErrorMessage from the Backend!
                    final ErrorMessage errorMessage = objectMapper.readValue(e.getResponseBodyAsByteArray(), ErrorMessage.class);
                    logger.info("The request to the backend failed. Reason id '{}: {}' Details: {} ", e.getStatusCode(), e.getStatusText(), errorMessage);
                } catch (Exception jx) {
                    // Unable to parse response body
                    logger.info("The request to the backend failed. URI: {} Reason id '{}: {}' Message: {}", url, e.getStatusCode(), e.getStatusText(), jx.getMessage());
                    throw new NexusHttpException("An internal error occurred on the backend. URI: " + url + " Reason id '" + e.getStatusCode()+ "'");
                }
                // let back BAD_REQUEST!
                //if (e.getStatusCode() != HttpStatus.BAD_REQUEST) {
                //   throw new NexusHttpException("An internal error occurred on the backend. URI: " + url + " Reason id '" + e.getStatusCode()+ "'");
                //}
            default:
                throw e;
        }
    }

    /**
     * Create a new ${@link RequestEntity} with additional user and product headers
     *
     * @param body  Object
     * @param headers HttpHeaders
     * @return RequestEntity
     */
    public HttpEntity<Object> createRequestEntity(Object body, HttpHeaders headers) {
        if (headers == null || removeHeaders) {
            headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON); // mandatory forced!
            headers.add(HttpHeaders.USER_AGENT, userAgent);  // mandatory forced, some RestApi filter the User-Agent!
        }

        // Some RestApi can filter the Host header! (localhost)
        if (removeHostHeader) headers.remove(HttpHeaders.HOST);
        if (removeOriginHeader) headers.remove(HttpHeaders.ORIGIN);

        // Basic Authentication, Bearer Authentication and Cookies
        // WARN BasicAuth Not UTF-8! see https://datatracker.ietf.org/doc/html/rfc7617#page-14
        if (!ObjectUtils.isEmpty(username) && !ObjectUtils.isEmpty(password))
            headers.setBasicAuth(HttpHeaders.encodeBasicAuth(username, password, StandardCharsets.ISO_8859_1));
        if (!ObjectUtils.isEmpty(bearer))
            headers.setBearerAuth(bearer);
        if (!ObjectUtils.isEmpty(cookie))
            headers.add(HttpHeaders.COOKIE, cookie);

        logger.debug("Requested Headers: {}", headers);
        if (body != null) return new HttpEntity<>(body, headers);
        return new HttpEntity<>(headers);
    }

    @Override
    public <T> ResponseType<T> createResponseType(Class<T> responseType) {
        Assert.notNull(responseType, "responseType may not be null!");
        return new ResponseTypeImpl<>(responseType);
    }

    @Override
    public <T> ResponseType<T> createResponseType(ParameterizedTypeReference<T> responseType) {
        Assert.notNull(responseType, "responseType may not be null!");
        return new ResponseTypeImpl<>(responseType);
    }

    @Override
    public String getBackendURL() {
        return this.backendURL;
    }

    @Override
    public boolean isRemovedHeaders() {
        return this.removeHeaders || this.removeHostHeader || this.removeOriginHeader;
    }

    private static class ResponseTypeImpl<T> implements ResponseType<T> {

        private final Class<T> responseClass;
        private final ParameterizedTypeReference<T> parameterizedType;

        private ResponseTypeImpl() { this(null, null); }

        ResponseTypeImpl(Class<T> responseClass) { this(responseClass, null); }

        ResponseTypeImpl(ParameterizedTypeReference<T> parameterizedType) { this(null, parameterizedType); }

        private ResponseTypeImpl(Class<T> responseClass, ParameterizedTypeReference<T> parameterizedType) {
            this.responseClass = responseClass;
            this.parameterizedType = parameterizedType;
        }

        @Override
        public Class<T> getResponseClass() { return responseClass; }

        @Override
        public ParameterizedTypeReference<T> getResponseParameterizedTypeReference() { return parameterizedType; }
    }

    public static class EntityBackend<T> {
        private final T body;
        private final HttpHeaders headers;
        private final HttpStatus status;

        public EntityBackend(T body, HttpHeaders headers, HttpStatus status) {
            this.body = body;
            this.headers = headers;
            this.status = status;
        }

        public T getBody() {
            return this.body;
        }

        public HttpHeaders getHttpHeaders() {
            return this.headers;
        }

        public HttpStatus getStatus() {
            return this.status;
        }
    }

}
