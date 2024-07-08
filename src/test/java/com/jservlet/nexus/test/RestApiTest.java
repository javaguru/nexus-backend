package com.jservlet.nexus.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.controller.MockController.Data;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.EntityError;
import com.jservlet.nexus.shared.exceptions.*;
import junit.framework.TestCase;
import org.junit.Ignore;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

/**
 * Run tests with a local Tomcat localhost:8082/nexus-backend
 */
@Ignore
public class RestApiTest extends TestCase {

    public void testGetBackend() throws HttpClientErrorException, NexusGetException, NexusResourceNotFoundException {
        // init a BackendService
        BackendService backendService = new BackendServiceImpl();
        backendService.setBackendURL("http://localhost:8082/nexus-backend");
        backendService.setRestOperations(new RestTemplate());
        backendService.setObjectMapper(new ObjectMapper());

        // get Data
        Data data = backendService.get("/mock/v1/data", backendService.createResponseType(Data.class));
        System.out.println(data);
    }

    public void testGetEntity() throws NexusIllegalUrlException, NexusHttpException {
        // init a BackendService
        BackendService backendService = new BackendServiceImpl();
        backendService.setBackendURL("http://localhost:8082/nexus-backend");
        backendService.setRestOperations(new RestTemplate());
        backendService.setObjectMapper(new ObjectMapper());

        try {
            // get Object Entity
            Object obj = backendService.doRequest("/mock/v1/data", HttpMethod.GET,
                    backendService.createResponseType(Data.class), null, null);
            System.out.println(obj);
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // catch and return any HttpError!
            System.out.println(e.getMessage());
        }
    }

    public void testGeEntityError() throws NexusIllegalUrlException, NexusHttpException {
        // init a BackendService
        BackendService backendService = new BackendServiceImpl();
        backendService.setBackendURL("http://localhost:8082/nexus-backend");
        backendService.setRestOperations(new RestTemplate());
        backendService.setObjectMapper(new ObjectMapper());

        try {
            // get Error Entity
            Object objError = backendService.doRequest("/mock/v1/dataXss?param1=<script>alert('info1')</script>",
                    HttpMethod.GET, backendService.createResponseType(Data.class), null, null);

            // Handle an Entity Error!
            if (objError instanceof EntityError)
                System.out.println(((EntityError<?>) objError).getBody() + " " + ((EntityError<?>) objError).getStatus());
            else
                System.out.println(objError);

        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // catch and return any HttpError!
            System.out.println(e.getMessage());
        }
    }

    public void testGetResource() {
        // init a BackendService
        BackendService backendService = new BackendServiceImpl();
        backendService.setBackendURL("http://localhost:8082/nexus-backend");
        backendService.setRestOperations(new RestTemplate());
        backendService.setObjectMapper(new ObjectMapper());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
            Resource resource = backendService.doRequest("/mock/v1/datafile", HttpMethod.GET,
                    backendService.createResponseType(Resource.class), null, headers);
            System.out.println(resource);

        }
        catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET File entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException e) {
            System.out.println("ResourceNotFound: " + e.getMessage());
        }

    }

    public void testNotFoundGetEntity() throws NexusIllegalUrlException, NexusHttpException {
        // init a BackendService
        BackendServiceImpl backendService = new BackendServiceImpl();
        backendService.setBackendURL("http://localhost:8082/nexus-backend");
        backendService.setRestOperations(new RestTemplate());
        backendService.setObjectMapper(new ObjectMapper());

        try {
            // get Object Entity
            Object obj = backendService.doRequest("/mock/v1/dataNotFound", HttpMethod.GET,
                    backendService.createResponseType(Data.class), null, null);
            System.out.println(obj);
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // catch and return any HttpError!
            System.out.println(e.getMessage());
        }
    }
}
