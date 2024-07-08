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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.exceptions.*;
import org.apache.commons.codec.binary.Base64;
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
 *     <li>nexus.backend.url: set the URL to access the backend</li>
 * </ul>
 */
@Service
public class BackendServiceImpl implements BackendService {

    private static final Logger logger = LoggerFactory.getLogger(BackendServiceImpl.class);

    private static final ResponseType<Void> EMPTY_RESPONSE = new ResponseTypeImpl<>();

    private String backendURL;

    private RestOperations restOperations;

    private ObjectMapper objectMapper;

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

    @Value("${nexus.backend.header.user-agent:JavaNexus}")
    private String userAgent;


    @Value("${nexus.backend.exception.http500:true}")
    private boolean isHttp500;
    @Value("${nexus.backend.exception.http400:true}")
    private boolean isHttp400;
    @Value("${nexus.backend.exception.http401:true}")
    private boolean isHttp401;
    @Value("${nexus.backend.exception.http405:true}")
    private boolean isHttp405;


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

    @SuppressWarnings("unchecked")
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
            // handle specific RestClientResponseException by object
            if (isHandleHttpState(e.getStatusCode())) {
                try {
                    return handleResponse(new ResponseEntity<>((T) objectMapper.readValue(e.getResponseBodyAsString(), Object.class), e.getStatusCode()));
                }
                catch (Exception jx) {
                    // Unable to parse response body
                    logger.info("The request to the backend failed. Url {} Method {} Reason id '{}: {}' Message: {}",
                            url, method, e.getStatusCode(), e.getStatusText(), jx.getMessage());
                    // let the default backend ErrorMessage!
                }
            }
            //  handle default ErrorMessage or exception..
            return handleResponseError(url, e);
        }
    }

    /**
     * Returns true if a specific HttpStatus has to be handled by the configuration
     *
     * @param status Ht tpStatus
     * @return true Returns true if HttpStatus has to be handled
     */
    private boolean isHandleHttpState(HttpStatus status) {
        return isHttp400 && status == HttpStatus.BAD_REQUEST ||
               isHttp401 && status == HttpStatus.UNAUTHORIZED ||
               isHttp405 && status == HttpStatus.METHOD_NOT_ALLOWED ||
               isHttp500 && status == HttpStatus.INTERNAL_SERVER_ERROR;
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
        return backendURL + url;
    }

    @SuppressWarnings("unchecked")
    private <T> T handleResponse(ResponseEntity<T> exchange) {
        T responseBody = exchange.getBody();
        if (logger.isDebugEnabled()) {
            logger.debug("Headers response: {}", exchange.getHeaders());
            if (responseBody != null) {
                logger.debug("The response is: {} {}", exchange.getStatusCode(), LogFormatUtils.formatValue(responseBody, truncated));
            } else {
                logger.debug("The response is empty with HttpState: {}", exchange.getStatusCode());
            }
        }
        if (responseBody == null) return (T) exchange.getStatusCode();
        if (isHandleHttpState(exchange.getStatusCode()))
            return (T) new EntityError<>(exchange.getBody(), exchange.getStatusCode());
        return exchange.getBody();
    }

    private <T> T handleResponseError(String url, HttpStatusCodeException e) throws NexusResourceNotFoundException, NexusHttpException {
        switch (e.getStatusCode()) {
            case NOT_FOUND: throw new NexusResourceNotFoundException("Failed to request to the backend. Resource not found. URI: " + url);
            case CONFLICT: throw new NexusResourceExistsException("Resource already exist. URI: " + url);
            case METHOD_NOT_ALLOWED:
            case UNAUTHORIZED:
            case INTERNAL_SERVER_ERROR:
                try {
                    // The default response ErrorMessage!
                    final ErrorMessage errorMessage = objectMapper.readValue(e.getResponseBodyAsString(), ErrorMessage.class);
                    logger.info("The request to the backend failed. Reason id '{}: {}' Details: {} ", e.getStatusCode(), e.getStatusText(), errorMessage);
                } catch (Exception jx) {
                    // Unable to parse response body
                    logger.info("The request to the backend failed. URI: {} Reason id '{}: {}' Message: {}", url, e.getStatusCode(), e.getStatusText(), jx.getMessage());
                }
                // let back BAD_REQUEST!
                if (e.getStatusCode() != HttpStatus.BAD_REQUEST) {
                   throw new NexusHttpException("An internal error occurred on the backend. URI: " + url + " Reason id '" + e.getStatusCode()+ "'");
                }
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
            headers.add("User-Agent", userAgent);  // mandatory forced, some RestApi filter the User-Agent!
        }

        // Some RestApi can filter the Host header! (localhost)
        if (removeHostHeader) headers.remove("host");
        if (removeOriginHeader) headers.remove("origin");

        // Apply ID headers.set("X-ID", sessionid);
        if (!ObjectUtils.isEmpty(username) && !ObjectUtils.isEmpty(password)) createAuthorizationHeaders(headers, username, password);
        if (!ObjectUtils.isEmpty(cookie)) createCookieHeaders(headers, cookie);
        if (!ObjectUtils.isEmpty(bearer)) createAuthorizationBearerHeaders(headers, bearer);
        logger.debug("Headers requested: {}", headers);
        if (body != null) return new HttpEntity<>(body, headers);
        return new HttpEntity<>(headers);
    }

    /**
     * Create http Headers with cookie
     * @param httpHeaders       HttpHeaders
     */
    private static void createCookieHeaders(HttpHeaders httpHeaders, String cookie) {
        httpHeaders.add("Cookie", cookie);
    }

    /**
     * Create http Headers with Basic Authorization header with username/password
     *
     * @param httpHeaders   HttpHeaders
     * @param username      String username
     * @param password      String password
     */
    private static void createAuthorizationHeaders(HttpHeaders httpHeaders, String username, String password) {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.US_ASCII));
        String authHeader = "Basic " + new String(encodedAuth);
        httpHeaders.add("Authorization", authHeader);
    }

    /**
     * Create http Headers with Bearer Authorization header with token
     *
     * @param httpHeaders   HttpHeaders
     * @param bearer      String bearer token
     */
    private static void createAuthorizationBearerHeaders(HttpHeaders httpHeaders, String bearer) {
        String authHeader = "Bearer " + bearer;
        httpHeaders.add("Authorization", authHeader);
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

    public static class EntityError<T> {
        private final T body;
        private final HttpStatus status;

        public EntityError(T body, HttpStatus status) {
            this.body = body;
            this.status = status;
        }

        public T getBody() {
            return body;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    public static class ErrorMessage {

        @JsonProperty(required = true)
        private Message error;

        public ErrorMessage(String code, String source, String text) {
            this.error = new Message(code, "ERROR", source, text);
        }

        public Message getError() { return this.error; }

        public void setError(Message error) { this.error = error; }

        public String toString() { return "ErrorMessage{error=" + this.error + "}"; }
    }

    public static class Message {
        private String code;
        private String level;
        private String source;
        private String text;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, String> parameters = new HashMap<>();

        public Message(String code, String level, String source, String text) {
            this.code = code;
            this.level = level;
            this.source = source;
            this.text = text;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        public void setParameter(String text) {
            this.parameters.put(text, "");
        }

        @Override
        public String toString() {
            return "Message{" +
                    "code='" + code + '\'' +
                    ", level='" + level + '\'' +
                    ", source='" + source + '\'' +
                    ", text='" + text + '\'' +
                    ", parameters=" + parameters +
                    '}';
        }
    }


    @Override
    public String getBackendURL() {
        return this.backendURL;
    }

    @Override
    public boolean isRemovedHeaders() {
        return this.removeHeaders || this.removeHostHeader || this.removeOriginHeader;
    }

}
