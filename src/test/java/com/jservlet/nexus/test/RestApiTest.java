package com.jservlet.nexus.test;

import com.jservlet.nexus.controller.MockController.Data;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendService.ResponseType;
import com.jservlet.nexus.shared.exceptions.*;
import com.jservlet.nexus.test.config.ApplicationTestConfig;
import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Run tests with a local Tomcat localhost:8082/nexus-backend
 */
@Ignore // Remove for unit test
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes={ApplicationTestConfig.class})
@TestPropertySource(properties = { "nexus.backend.url=http://localhost:8082/nexus-backend" })
public class RestApiTest extends TestCase {

    private static final Logger logger = LoggerFactory.getLogger(RestApiTest.class);

    @Autowired
    private BackendService backendService;

    /**
     * Test ListEntity
     */
    @Test
    public void testListEntity() {
        try {
            List<Data> list = getListData();
            logger.info(list.toString());
        } catch (NexusGetException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Example standardized method get List Data Request
     *
     * @return List<Data>
     * @throws NexusGetException  An Exception occurred during the get Request
     */
    private List<Data> getListData() throws NexusGetException {
        String url = "/mock/v1/dataList"; // dataListx Resource not found
        try {
            // get List Data Object
            ResponseType<List<Data>> typeReference = backendService.createResponseType(new ParameterizedTypeReference<>(){});
            // Apply converter before, if needed!
            return backendService.get(url, typeReference);
        } catch (NexusGetException ngex) {
            // GetException logged and propagated
            logger.error("An error occurred while get ListData by url '{}'", url);
            throw ngex;
        } catch (NexusResourceNotFoundException ex) {
            // NotFound error logged and GetException propagated
            logger.error("An error occurred while get ListData by url '{}'. ", url);
            throw new NexusGetException(ex.getMessage());
        }
    }

    /**
     * Test ListEntity
     */
    @Test
    public void testPutPostListEntity() {
        try {
            List<Data> list = getListData();
            logger.info("Put or Post List Entity: {}", putPostListData(list));
        } catch (NexusGetException e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Example standardized method Put or Post List Data Request (or File Request...)
     *
     * @param dataList   List data
     * @return  An Exception occurred during the get Request
     */
    private boolean putPostListData(List<Data> dataList) {
        // Try to update existing -> if it fails (Response Code: 404), try to create a new
        String url = "/mock/v1/dataPostList";
        // get List Data Object
        ResponseType<Boolean> typeReference = backendService.createResponseType(Boolean.class);
        try {
            try {
                // Try to update dataList
                return backendService.put(url, dataList, typeReference);
            } catch (NexusResourceNotFoundException notFound) {
                logger.warn("Update dataList not possible due to missing backend entry! '{}' ", dataList);
                // Try to create dataList
                return backendService.post(url, dataList, typeReference);
            }
        } catch (NexusResourceExistsException | NexusCreationException e) {
            // Create new also failed !?
            logger.warn("Error occurred in create new dataList: {}", dataList, e);
        } catch (NexusModificationException e) {
            // Error occurred while updating existing dataList
            logger.warn("Error occurred while updating existing dataList: {}", dataList, e);
        }
        return false;
    }

    /* WARN Tests doRequest */

    @Test
    public void testGetEntity() {
         try {
            // get List Data Object
            ResponseType<List<Data>> typeReference = backendService.createResponseType(new ParameterizedTypeReference<>(){});
            Object obj = backendService.doRequest("/mock/v1/dataList", HttpMethod.GET, typeReference, null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
             System.out.println("Failed to List<Data> String entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testGetEntityError() {
        try {
            // get Error Entity
            Object objError = backendService.doRequest("/mock/v1/dataXss?param1=<script>alert('info1')</script>",
                    HttpMethod.GET, backendService.createResponseType(Data.class), null, null);
            System.out.println(objError);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET EntityError entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }


    @Test
    public void testGetString() {
        try {
            // get String
            String obj = backendService.doRequest("/mock/v1/dataList", HttpMethod.GET,
                    backendService.createResponseType(String.class), null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET String entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testGetArrayList() {
        try {
            // get ArrayList
            Object obj = backendService.doRequest("/mock/v1/dataList", HttpMethod.GET,
                    backendService.createResponseType(Object.class), null, null);
            if (obj instanceof ArrayList<?>) {
                System.out.println(obj);
            }
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET ArrayList entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }


    @Test
    public void testGetResource() {
        try {
            // get Resource file in ByteArray!
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
            Resource resource = backendService.doRequest("/mock/v1/datafile", HttpMethod.GET,
                    backendService.createResponseType(Resource.class), null, headers);  // WARN mandatory typed Resource.class
            String data = StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
            System.out.println(data);
        }
        catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET Resource entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException | IOException e) {
            System.out.println("ResourceNotFound: " + e.getMessage());
        }
    }


    @Test
    public void testGetByteArray() {
        try {
            // get data in ByteArray!
            byte[] obj = backendService.doRequest("/mock/v1/dataBytes", HttpMethod.GET,
                    backendService.createResponseType(byte[].class), null, null); // WARN mandatory typed byte[].class
            System.out.println(new String(obj, StandardCharsets.UTF_8));
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET Bytes entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testEchoProxy() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            // get Echo data in ByteArray through the proxy!
            byte[] obj = backendService.doRequest("/mock/v1/proxy", HttpMethod.POST,
                    backendService.createResponseType(byte[].class), "echo=Hello Proxy! Héhè hàhâ ", headers); // WARN mandatory typed byte[].class
            System.out.println(new String(obj, StandardCharsets.UTF_8));
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to POST Echo Bytes entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testGetEchoEntityProxy() {
        try {
            // get Echo data in ByteArray through the proxy!
            byte[] obj = backendService.doRequest("/mock/v1/echo?echo=Hello+Proxy!", HttpMethod.GET,
                    backendService.createResponseType(byte[].class), null, null); // WARN mandatory typed byte[].class
            System.out.println(new String(obj, StandardCharsets.UTF_8));
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to POST Echo Bytes entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testPostEchoEntityProxy() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE); // WARN mandatory!
            // get Echo data in ByteArray through the proxy!
            byte[] obj = backendService.doRequest("/mock/v1/proxy", HttpMethod.POST,
                    backendService.createResponseType(byte[].class), "Hello Echo!", headers); // WARN mandatory typed byte[].class
            System.out.println(new String(obj, StandardCharsets.UTF_8));
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to POST Echo Bytes entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testNotFoundGetEntity() {
        try {
            // get Object Entity
            Object obj = backendService.doRequest("/mock/v1/dataNotFound", HttpMethod.GET,
                    backendService.createResponseType(Data.class), null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET Data entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testXErrorBackend500() {
        try {
            // get Object Entity
            Object obj = backendService.doRequest("/mock/v1/dataError500", HttpMethod.GET,
                    backendService.createResponseType(Data.class), null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET Data entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testXErrorBackend401() {
        try {
            // get Object Entity
            Object obj = backendService.doRequest("/mock/v1/dataError401", HttpMethod.GET,
                    backendService.createResponseType(Data.class), null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET Data entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
        }
    }

    @Test
    public void testXErrorBackend400() throws NexusGetException {
        try {
            // get Object Entity
            Object obj = backendService.doRequest("/v1/dataError400", HttpMethod.GET,
                    backendService.createResponseType(Data.class), null, null);
            System.out.println(obj);
        } catch (NexusHttpException | NexusIllegalUrlException | HttpStatusCodeException e) {
            System.out.println("Failed to GET Data entity/entities on backend: " + e.getMessage());
        } catch (NexusResourceNotFoundException ex) {
            System.out.println("ResourceNotFound: " + ex.getMessage());
            //throw new NexusGetException(ex.getMessage());
        }
    }


}
