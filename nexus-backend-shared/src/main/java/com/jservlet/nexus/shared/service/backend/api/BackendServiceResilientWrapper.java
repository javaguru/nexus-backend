package com.jservlet.nexus.shared.service.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.service.backend.BackendConfigProperties;
import com.jservlet.nexus.shared.service.backend.BackendService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.jservlet.nexus.shared.exceptions.*;
import org.springframework.web.client.RestOperations;

/**
 * Wrapper BackendService Resilient4j
 */
public class BackendServiceResilientWrapper implements BackendService {

    private static final String UNAVAILABLE = "Backend service is temporarily unavailable. Please try again later.";
    private static final String UNAVAILABLE_FILE = "Backend service is temporarily unavailable. Unable to retrieve the file. Please try again later.";
    private static final String UNAVAILABLE_COM = "Communication error with the backend after several attempts.";

    private final BackendService backendService;
    private final CircuitBreaker circuitBreaker;

    private final Retry retry;

    public BackendServiceResilientWrapper(BackendService backendService,
                                          CircuitBreaker myCircuitBreaker,
                                          Retry myRetry) {
        this.backendService = backendService;
        this.circuitBreaker = myCircuitBreaker;
        this.retry = myRetry;
    }

    @Override
    public <T> T get(String path, ResponseType<T> responseType) throws NexusGetException, NexusResourceNotFoundException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.get(path, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusGetException | NexusResourceNotFoundException e) {
            // Business exceptions (e.g., 404) normally pass through
            throw e;
        } catch (CallNotPermittedException e) {
            // Specific exception from Resilience4j: The circuit is OPEN!
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        } catch (Exception e) {
            // All other errors (Network, Timeout, or if the Retry has exhausted its attempts)
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }

    @Override
    public Resource getFile(String url) throws NexusGetException, NexusResourceNotFoundException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.getFile(url))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusGetException | NexusResourceNotFoundException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_FILE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }

    @Override
    public <T> T post(String url, Object request, ResponseType<T> responseType)
            throws NexusCreationException, NexusResourceExistsException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.post(url,  request, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusCreationException | NexusResourceExistsException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }

    @Override
    public <T> T postFile(String url, Resource resource, ResponseType<T> responseType)
            throws NexusCreationException, NexusResourceExistsException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.postFile(url, resource, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusCreationException | NexusResourceExistsException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_FILE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }

    @Override
    public <T> T put(String url, Object request, ResponseType<T> responseType)
            throws NexusModificationException, NexusResourceExistsException, NexusResourceNotFoundException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.put(url,  request, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusModificationException | NexusResourceExistsException | NexusResourceNotFoundException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }


    @Override
    public <T> T putFile(String url, Resource resource, ResponseType<T> responseType)
            throws NexusModificationException, NexusResourceNotFoundException, NexusResourceExistsException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.putFile(url, resource, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusModificationException | NexusResourceNotFoundException | NexusResourceExistsException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_FILE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }


    @Override
    public void delete(String url) throws NexusDeleteException, NexusResourceNotFoundException, NexusServiceUnavailableException {
        try {
            Decorators.ofCheckedRunnable(() -> backendService.delete(url))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .run();
        } catch (NexusDeleteException | NexusResourceNotFoundException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T patch(String url, Object request, ResponseType<T> responseType)
            throws NexusModificationException, NexusResourceExistsException, NexusResourceNotFoundException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.patch(url,  request, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusModificationException | NexusResourceExistsException | NexusResourceNotFoundException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }

    @Override
    public <T> T patchFile(String url, Resource resource, ResponseType<T> responseType)
            throws NexusModificationException, NexusResourceNotFoundException, NexusResourceExistsException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.patchFile(url, resource, responseType))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusModificationException | NexusResourceExistsException | NexusResourceNotFoundException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_FILE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        }
    }

    @Override
    public <T> T doRequest(String url, HttpMethod method, ResponseType<T> responseType, Object body, HttpHeaders headers)
            throws NexusResourceNotFoundException, NexusHttpException, NexusIllegalUrlException, NexusServiceUnavailableException {
        try {
            return Decorators.ofCallable(() -> backendService.doRequest(url, method, responseType, body, headers))
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate()
                    .call();
        } catch (NexusResourceNotFoundException | NexusHttpException | NexusIllegalUrlException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE, e);
        } catch (Exception e) {
            throw new NexusServiceUnavailableException(UNAVAILABLE_COM, e);
        }
    }

    @Override
    public <T> ResponseType<T> createResponseType(Class<T> clazz) {
        return backendService.createResponseType(clazz);
    }

    @Override
    public <T> ResponseType<T> createResponseType(ParameterizedTypeReference<T> typeRef) {
        return backendService.createResponseType(typeRef);
    }

    @Override
    public void setBackendURL(String backendURL) {
        backendService.setBackendURL(backendURL);
    }

    @Override
    public String getBackendURL() {
        return backendService.getBackendURL();
    }

    @Override
    public void setRestOperations(RestOperations restOperations) {
        backendService.setRestOperations(restOperations);
    }

    @Override
    public void setObjectMapper(ObjectMapper objectMapper) {
        backendService.setObjectMapper(objectMapper);
    }

    @Override
    public void setConfig(BackendConfigProperties backendConfigProperties) {
        backendService.setConfig(backendConfigProperties);
    }

    @Override
    public boolean isRemovedHeaders() {
        return backendService.isRemovedHeaders();
    }


}
