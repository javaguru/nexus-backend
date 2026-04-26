package com.jservlet.nexus.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.exceptions.NexusHttpException;
import com.jservlet.nexus.shared.exceptions.NexusIllegalUrlException;
import com.jservlet.nexus.shared.exceptions.NexusResourceNotFoundException;
import com.jservlet.nexus.shared.service.backend.BackendConfigProperties;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DummyTest {

    @Test
    public void dummyTest() {
        assertEquals("dummy","dummy");
    }


    public static void main(String[] args) {
        // New instance BackendService
        BackendService backendService = new BackendServiceImpl();
        backendService.setConfig(new BackendConfigProperties());
        backendService.setBackendURL("https://postman-echo.com");
        backendService.setRestOperations(new RestTemplate());
        backendService.setObjectMapper(new ObjectMapper());

        try {
            // get String
            String obj = backendService.doRequest("/get?foo1=bar1&foo2=bar2", HttpMethod.GET,
                    backendService.createResponseType(String.class), null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET String entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }
}
