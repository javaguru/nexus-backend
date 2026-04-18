package com.jservlet.nexus.test;

import com.jservlet.nexus.controller.MockController.Data;
import com.jservlet.nexus.shared.exceptions.*;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendService.ResponseType;
import com.jservlet.nexus.test.config.ApplicationTestConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;

// New imports JUnit 5 and Spring Extension
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Run tests with a local Tomcat localhost:8082/nexus-backend
 */
@Disabled("Remove for unit test") // Replace @Ignore
@SpringBootTest(classes={ApplicationTestConfig.class})
class RestControllerTest { // No "extends TestCase" no "implements ResourceLoaderAware"

    private static final Logger logger = LoggerFactory.getLogger(RestControllerTest.class);

    @DynamicPropertySource
    static void registerBackendUrl(DynamicPropertyRegistry registry) {
        registry.add("nexus.backend.url", () -> "http://localhost:8082/nexus-backend");
    }

    @Autowired
    private BackendService backendService;

    @Autowired
    private ResourceLoader resourceLoader;

    private ResponseType<Void> NO_RESPONSE_TYPE;
    private ResponseType<Data> DATA_RESPONSE_TYPE;
    private ResponseType<List<Data>> DATA_LIST_RESPONSE_TYPE;
    private ResponseType<HttpStatus> STATUS_RESPONSE_TYPE;
    private ResponseType<Boolean> BOOL_RESPONSE_TYPE;
    private ResponseType<Object> OBJECT_RESPONSE_TYPE;
    private ResponseType<byte[]> BYTES_RESPONSE_TYPE;

    private final static String pathImage = "/static/images";
    private final static String nameImage = "/logo-marianne.svg";

    private final static String urlImage = pathImage + nameImage;
    private final static String fileImage = System.getProperty("java.io.tmpdir") + nameImage;

    @BeforeEach // Replace @Before
    void setUp() {
        NO_RESPONSE_TYPE = backendService.createResponseType(Void.class);
        DATA_RESPONSE_TYPE = backendService.createResponseType(Data.class);
        DATA_LIST_RESPONSE_TYPE = backendService.createResponseType(new ParameterizedTypeReference<>(){});
        BOOL_RESPONSE_TYPE = backendService.createResponseType(Boolean.class);
        STATUS_RESPONSE_TYPE = backendService.createResponseType(HttpStatus.class);
        OBJECT_RESPONSE_TYPE = backendService.createResponseType(Object.class);
        BYTES_RESPONSE_TYPE = backendService.createResponseType(byte[].class);
    }

    @Test
    void testGetBytesBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/dataBytes";
        byte[] data = backendService.get(url, BYTES_RESPONSE_TYPE);
        logger.debug(Arrays.toString(data));
        logger.debug(new String(data, StandardCharsets.UTF_8));
        MatcherAssert.assertThat(new String(data, StandardCharsets.UTF_8),
                CoreMatchers.containsString("GET_BYTES"));
    }

    @Test
    void testGetBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        assertNotNull(data);
    }

    @Test
    void testGetListBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/dataList";
        List<Data> list = backendService.get(url, DATA_LIST_RESPONSE_TYPE);
        assertNotNull(list);
    }

    @Test
    void testPostBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusCreationException, NexusResourceExistsException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        Boolean status = backendService.post(url, data, BOOL_RESPONSE_TYPE);
        assertNotNull(status);
        assertTrue(status);
    }

    @Test
    void testPutBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        Boolean status = backendService.put(url, data, BOOL_RESPONSE_TYPE);
        assertNotNull(status);
        assertTrue(status);
    }

    @Test
    void testPatchBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        Boolean status = backendService.patch(url, data, BOOL_RESPONSE_TYPE);
        assertNotNull(status);
        assertTrue(status);
    }

    @Test
    void testPatchFileBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException, IOException {
        Resource image = backendService.getFile(urlImage);
        assertNotNull(image); // Remplacement de Assert.assertNotNull
        if (image.exists()) {
            File tempFile = new File(fileImage);
            FileUtils.copyInputStreamToFile(image.getInputStream(), tempFile);
            String url = "/mock/v1/datafile";
            HttpStatus status = backendService.patchFile(url, new FileSystemResource(tempFile), STATUS_RESPONSE_TYPE);
            assertNotNull(status);
            assertTrue(status.is2xxSuccessful());
        }
    }

    @Test
    void testGetFileBackend() throws NexusGetException, NexusResourceNotFoundException, IOException {
        Resource image = backendService.getFile(urlImage);
        logger.debug(String.valueOf(image.contentLength()));
        logger.debug(image.getFilename());
        logger.debug(new String(IOUtils.toCharArray(image.getInputStream(), StandardCharsets.UTF_8)));
        FileUtils.copyInputStreamToFile(image.getInputStream(), new File(fileImage));
        assertNotNull(image);
    }

    @Test
    void testPostFileBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusCreationException, NexusResourceExistsException, IOException {
        Resource image = backendService.getFile(urlImage);
        assertNotNull(image);
        if (image.exists()) {
            File tempFile = new File(fileImage);
            FileUtils.copyInputStreamToFile(image.getInputStream(), tempFile);
            String url = "/mock/v1/datafile";
            HttpStatus status = backendService.postFile(url, new FileSystemResource(tempFile), STATUS_RESPONSE_TYPE);
            assertNotNull(status);
            assertTrue(status.is2xxSuccessful());
        }
    }

    @Test
    void testPutFileBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException, IOException {
        Resource image = backendService.getFile(urlImage);
        assertNotNull(image);
        if (image.exists()) {
            File tempFile = new File(fileImage);
            FileUtils.copyInputStreamToFile(image.getInputStream(), tempFile);
            String url = "/mock/v1/datafile";
            HttpStatus status = backendService.putFile(url, new FileSystemResource(tempFile), STATUS_RESPONSE_TYPE);
            assertNotNull(status);
            assertTrue(status.is2xxSuccessful());
        }
    }

    @Test
    void testDeleteFileBackend() throws NexusResourceNotFoundException, NexusDeleteException {
        String url = "/mock/v1/datafile";
        backendService.delete(url);
        assertTrue(true);
    }

    @Test
    void testEchoBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/echo";
        byte[] data = backendService.get(url, BYTES_RESPONSE_TYPE);
        logger.debug(Arrays.toString(data));
        logger.debug(new String(data, StandardCharsets.UTF_8));
        MatcherAssert.assertThat(new String(data, StandardCharsets.UTF_8),
                CoreMatchers.containsString("echo"));
    }

    @Test
    void testEchoProxyBackend() throws NexusCreationException, NexusResourceExistsException {
        String url = "/mock/v1/proxy";
        byte[] data = backendService.post(url, null, BYTES_RESPONSE_TYPE);
        logger.debug(Arrays.toString(data));
        logger.debug(new String(data, StandardCharsets.UTF_8));
        MatcherAssert.assertThat(new String(data, StandardCharsets.UTF_8),
                CoreMatchers.containsString("echo"));
    }

    /* Http Errors */
    @Test
    void testGetXssBackendForbidden() {
        String url = "/mock/v1/dataXss?param1=<script>alert('info1')</script>";
        NexusGetException exception = assertThrows(NexusGetException.class, () -> {
            backendService.get(url, OBJECT_RESPONSE_TYPE);
        });
        assertTrue(exception.getMessage().contains("403") || exception.getCause().getMessage().contains("403"),
                "Should be an HTTP 403 Forbidden");
    }


    @Test
    void testPostXssBackendForbidden() {
        String url = "/mock/v1/dataPostXss?param1=test";
        Data data = new Data("info1","<script>alert(\"test\")</script>",  0.0006);
        //Data data = new Data("info1","<script>alert('test')</script>",  0.0006);

        NexusCreationException exception = assertThrows(
                NexusCreationException.class,
                () -> backendService.post(url, data, OBJECT_RESPONSE_TYPE)
        );

        assertTrue(exception.getMessage().contains("403") || exception.getCause().getMessage().contains("403"),
                "Should be an HTTP 403 Forbidden");
    }

    @Test
    void testXErrorBackend400() throws NexusResourceNotFoundException {
        try {
            String url = "/mock/v1/dataError400";
            backendService.get(url, OBJECT_RESPONSE_TYPE);
        } catch (NexusGetException e) {
            testException(e);
        }
    }

    @Test
    void testXErrorBackend500() throws NexusResourceNotFoundException {
        try {
            String url = "/mock/v1/dataError500";
            backendService.get(url, OBJECT_RESPONSE_TYPE);
        } catch (NexusGetException e) {
            testException(e);
        }
    }

    @Test
    void testXErrorBackend401() throws NexusResourceNotFoundException {
        try {
            String url = "/mock/v1/dataError401";
            backendService.get(url, OBJECT_RESPONSE_TYPE);
        } catch (NexusGetException e) {
            testException(e);
        }
    }

    private static void testException(NexusGetException e) {
        if (e.getCause() instanceof HttpClientErrorException) {
            HttpClientErrorException cause = (HttpClientErrorException) e.getCause();
            assertNotNull(cause.getStatusCode());
            logger.debug("Error: {} {}", cause.getStatusCode(), cause.getResponseBodyAsString());
        } else {
            assertNotNull(e.getMessage());
            logger.debug("Error: {}", e.getCause().getMessage());
        }
    }

}
