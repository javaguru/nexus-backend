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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestOperations;

/**
 * Rest BackendService
 */
public interface BackendService {

    /**
     * Execute a get request and returns the response as String
     *
     * @param <T>             The expected class of the value
     * @param url             The url to the backend to be executed
     * @param responseType    The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusGetException                   When the backend returns a 500
     * @throws NexusResourceNotFoundException      When the backend returns a 404
     */
    <T> T get(String url, ResponseType<T> responseType) throws NexusGetException, NexusResourceNotFoundException;

    /**
     * Execute a get request and returns the response as File
     * The response is of type Resource which contain the feed of type multipart and the headers
     *
     * @param url              The url to the backend to be executed
     * @return                 The File downloaded from the backend as a Resource
     * @throws NexusGetException                   When the backend returns a 500
     * @throws NexusResourceNotFoundException      When the backend returns a 404
     */
    Resource getFile(String url) throws NexusGetException, NexusResourceNotFoundException;

    /**
     * Execute a post request and returns the response as String
     * A post request is usually create a new entry and return it the response
     *
     * @param <T>             The expected class of the value.
     * @param url             The url to the backend to be executed
     * @param data            The entity to be create
     * @param responseType    The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusCreationException              When the backend returns a 500
     * @throws NexusResourceExistsException        When the backend returns a 404
     */
    <T> T post(String url, Object data, ResponseType<T> responseType) throws NexusCreationException, NexusResourceExistsException;

    /**
     * Execute a post with a File as multipart/form-data and returns the response.
     *
     * @param <T>              The expected class of the value
     * @param url              The url to the backend to be executed
     * @param resource         The file/data to be send
     * @param responseType     The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusCreationException               When the backend returns a 500
     * @throws NexusResourceExistsException         When the backend returns a 404
     */
    <T> T postFile(String url, Resource resource, ResponseType<T> responseType) throws NexusCreationException, NexusResourceExistsException;

    /**
     * Execute a put request and returns the response as String
     * A post request is usually modify an entry and return it the response
     *
     * @param <T>               The expected class of the value
     * @param url               The url to the backend to be executed
     * @param data              The entity to be modified
     * @param responseType      The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusModificationException            When the backend returns a 500
     * @throws NexusResourceExistsException          When the backend returns a 409
     * @throws NexusResourceNotFoundException        When the backend returns a 404
     */
    <T> T put(String url, Object data, ResponseType<T> responseType) throws NexusModificationException, NexusResourceExistsException, NexusResourceNotFoundException;

    /**
     * Execute a putFile request and returns the response as Resource
     * A putFile request is usually modify an entry and return it the response
     *
     * @param <T>               The expected class of the value
     * @param url               The url to the backend to be executed
     * @param resource          The file/data to be send
     * @param responseType      The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusModificationException            When the backend returns a 500
     * @throws NexusResourceExistsException          When the backend returns a 409
     * @throws NexusResourceNotFoundException        When the backend returns a 404
     */
    <T> T putFile(String url, Resource resource, ResponseType<T> responseType) throws NexusModificationException, NexusResourceNotFoundException, NexusResourceExistsException;

    /**
     * Execute a delete request, its a void
     * A post request is usually delete an entry and returns a http status
     *
     * @param url              The url to the backend to be executed
     * @throws NexusDeleteException                  When the backend returns a 500
     * @throws NexusResourceNotFoundException        When the backend returns a 404
     */
    void delete(String url) throws NexusDeleteException, NexusResourceNotFoundException;

    /**
     * Execute a patch request and returns the response as String
     * A patch request is usually update an entry and return it the response (Normally 204 No Content)
     *
     * @param <T>             The expected class of the value.
     * @param url             The url to the backend to be executed
     * @param data            The entity to be updated
     * @param responseType    The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusModificationException            When the backend returns a 500
     * @throws NexusResourceExistsException          When the backend returns a 500
     * @throws NexusResourceNotFoundException        When the backend returns a 404
     */
    <T> T patch(String url, Object data, ResponseType<T> responseType) throws NexusModificationException, NexusResourceExistsException, NexusResourceNotFoundException;

    /**
     * Execute a patchFile request and returns the response as Resource
     * A patchFile request is usually modify an entry and return it the response (Normally 204 No Content)
     *
     * @param <T>               The expected class of the value
     * @param url               The url to the backend to be executed
     * @param resource          The file/data to be send
     * @param responseType      The response type the result should be converted to
     * @return The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusModificationException            When the backend returns a 500
     * @throws NexusResourceExistsException          When the backend returns a 409
     * @throws NexusResourceNotFoundException        When the backend returns a 404
     */
    <T> T patchFile(String url, Resource resource, ResponseType<T> responseType) throws NexusModificationException, NexusResourceNotFoundException, NexusResourceExistsException;


    /**
     * Execute a request Rest BackendService
     *
     * @param <T>            The expected class of the value.
     * @param url            The url to the backend to be executed
     * @param method         The method
     * @param responseType   The response type the result should be converted to
     * @param body           The body if exist or null
     * @param headers        The headers
     * @return               The parsed response as an instance of type specified using the responseType parameter
     * @throws NexusResourceNotFoundException      When the backend returns a 404
     * @throws NexusHttpException                  When a http request to th backend fails.
     * @throws NexusIllegalUrlException            When a illegal url will be requested.
     */

    <T> T doRequest(String url, HttpMethod method, ResponseType<T> responseType, Object body, HttpHeaders headers)
            throws NexusResourceNotFoundException, NexusHttpException, NexusIllegalUrlException;

    <T> ResponseType<T> createResponseType(Class<T> responseType);

    <T> ResponseType<T> createResponseType(ParameterizedTypeReference<T> responseType);

    interface ResponseType<T> {
        Class<T> getResponseClass();
        ParameterizedTypeReference<T> getResponseParameterizedTypeReference();
    }

    void setBackendURL(String backendURL);
    void setRestOperations(RestOperations restOperations);
    void setObjectMapper(ObjectMapper objectMapper);

    String getBackendURL();
    boolean isRemovedHeaders();

}
