package com.jservlet.nexus.test;

import com.jservlet.nexus.controller.MockController.Data;
import com.jservlet.nexus.shared.exceptions.*;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendService.ResponseType;
import com.jservlet.nexus.test.config.ApplicationTestConfig;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Run tests with a local Tomcat localhost:8082/nexus-backend
 */
@Ignore // Remove for unit test
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes={ApplicationTestConfig.class})
@TestPropertySource(properties = { "nexus.backend.url=http://localhost:8082/nexus-backend" })
public class RestControllerTest extends TestCase implements ResourceLoaderAware  {

    private static final Logger logger = LoggerFactory.getLogger(RestControllerTest.class);

    @Autowired
    private BackendService backendService;

    private ResponseType<Void> NO_RESPONSE_TYPE;
    private ResponseType<Data> DATA_RESPONSE_TYPE;
    private ResponseType<List<Data>> DATA_LIST_RESPONSE_TYPE;
    private ResponseType<HttpStatus> STATUS_RESPONSE_TYPE;
    private ResponseType<Boolean> BOOL_RESPONSE_TYPE;
    private ResponseType<Object> ERROR_RESPONSE_TYPE;
    private ResponseType<byte[]> BYTES_RESPONSE_TYPE;

    private ResourceLoader resourceLoader;

    private final static String pathImage = "/static/images";
    private final static String nameImage = "/logo-marianne.svg";

    private final static String urlImage = pathImage + nameImage;
    private final static String fileImage = System.getProperty("java.io.tmpdir") + nameImage;

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Before
    public void setUp() {
        NO_RESPONSE_TYPE = backendService.createResponseType(Void.class);
        DATA_RESPONSE_TYPE = backendService.createResponseType(Data.class);
        DATA_LIST_RESPONSE_TYPE = backendService.createResponseType(new ParameterizedTypeReference<>(){});
        BOOL_RESPONSE_TYPE = backendService.createResponseType(Boolean.class);
        STATUS_RESPONSE_TYPE = backendService.createResponseType(HttpStatus.class);
        ERROR_RESPONSE_TYPE = backendService.createResponseType(Object.class);
        BYTES_RESPONSE_TYPE = backendService.createResponseType(byte[].class);
    }

    @Test
    public void testGetBytesBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/dataBytes";
        byte[] data = backendService.get(url, BYTES_RESPONSE_TYPE);
        logger.debug(Arrays.toString(data));
        logger.debug(new String(data, StandardCharsets.UTF_8));
        MatcherAssert.assertThat(new String(data, StandardCharsets.UTF_8),
                CoreMatchers.containsString("GET_BYTES"));
    }

    @Test
    public void testGetBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        assertNotNull(data);
    }

    @Test
    public void testGetListBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/dataList";
        List<Data> list = backendService.get(url, DATA_LIST_RESPONSE_TYPE);
        assertNotNull(list);
    }

    @Test
    public void testPostBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusCreationException, NexusResourceExistsException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        Boolean status = backendService.post(url, data, BOOL_RESPONSE_TYPE);
        assertNotNull(status);
        assertTrue(status);
    }

    @Test
    public void testPutBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        Boolean status = backendService.put(url, data, BOOL_RESPONSE_TYPE);
        assertNotNull(status);
        assertTrue(status);
    }

    @Test
    public void testPatchBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException {
        String url = "/mock/v1/data";
        Data data = backendService.get(url, DATA_RESPONSE_TYPE);
        Boolean status = backendService.patch(url, data, BOOL_RESPONSE_TYPE);
        assertNotNull(status);
        assertTrue(status);
    }

    @Test
    public void testPatchFileBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException {
        Resource image = backendService.getFile(urlImage);
        Assert.assertNotNull(image);
        if (image.exists()) {
            String url = "/mock/v1/datafile";
            HttpStatus status = backendService.patchFile(url, new FileSystemResource(new File(fileImage)), STATUS_RESPONSE_TYPE);
            assertNotNull(status);
            assertTrue(status.is2xxSuccessful());
        }
    }

    @Test
    public void testGetFileBackend() throws NexusGetException, NexusResourceNotFoundException, IOException {
        Resource image = backendService.getFile(urlImage);
        logger.debug(String.valueOf(image.contentLength()));
        logger.debug(image.getFilename());
        logger.debug(new String(IOUtils.toCharArray(image.getInputStream(), StandardCharsets.UTF_8)));
        FileUtils.copyInputStreamToFile(image.getInputStream(), new File(fileImage));
        assertNotNull(image);
    }

    @Test
    public void testPostFileBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusCreationException, NexusResourceExistsException {
        Resource image = backendService.getFile(urlImage);
        assertNotNull(image);
        if (image.exists()) {
            String url = "/mock/v1/datafile";
            HttpStatus status = backendService.postFile(url, new FileSystemResource(new File(fileImage)), STATUS_RESPONSE_TYPE);
            assertNotNull(status);
            assertTrue(status.is2xxSuccessful());
        }
    }

    @Test
    public void testPutFileBackend() throws NexusGetException, NexusResourceNotFoundException,
            NexusModificationException, NexusResourceExistsException {
        Resource image = backendService.getFile(urlImage);
        Assert.assertNotNull(image);
        if (image.exists()) {
            String url = "/mock/v1/datafile";
            HttpStatus status = backendService.putFile(url, new FileSystemResource(new File(fileImage)), STATUS_RESPONSE_TYPE);
            assertNotNull(status);
            assertTrue(status.is2xxSuccessful());
        }
    }

    @Test
    public void testDeleteFileBackend() throws NexusResourceNotFoundException, NexusDeleteException {
        String url = "/mock/v1/datafile";
        backendService.delete(url);
        assertTrue(true);
   }


    @Test
    public void testEchoBackend() throws NexusGetException, NexusResourceNotFoundException {
        String url = "/mock/v1/echo";
        byte[] data = backendService.get(url, BYTES_RESPONSE_TYPE);
        logger.debug(Arrays.toString(data));
        logger.debug(new String(data, StandardCharsets.UTF_8));
        MatcherAssert.assertThat(new String(data, StandardCharsets.UTF_8),
                CoreMatchers.containsString("echo"));
    }

    @Test
    public void testEchoProxyBackend() throws NexusCreationException, NexusResourceExistsException {
        String url = "/mock/v1/proxy";
        byte[] data = backendService.post(url, null, BYTES_RESPONSE_TYPE);
        logger.debug(Arrays.toString(data));
        logger.debug(new String(data, StandardCharsets.UTF_8));
        MatcherAssert.assertThat(new String(data, StandardCharsets.UTF_8),
                CoreMatchers.containsString("echo"));
    }


    /* Http Errors */
    @Test
    public void testGetXssBackend() throws NexusResourceNotFoundException {
        try {
            String url = "/mock/v1/dataXss?param1=<script>alert('info1')</script>"; //
            backendService.get(url, ERROR_RESPONSE_TYPE);
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

    @Test
    public void testPostXssBackend() throws NexusCreationException, NexusResourceExistsException {
        try {
            // try also security exception \\<script> or <script> only!
            String url = "/mock/v1/dataPostXss?param1=test";
            Data data = new Data("<script>alert('info1')</script>","info2", 0.0006);
            backendService.post(url, data, ERROR_RESPONSE_TYPE);
        } catch (NexusCreationException e) {
            assertNotNull(e.getMessage());
            logger.debug("Error: {}", e.getCause().getMessage());
        }
    }

    @Test
    public void testXErrorBackend400() throws NexusResourceNotFoundException {
        try {
           String url = "/mock/v1/dataError400";
           backendService.get(url, ERROR_RESPONSE_TYPE);
        } catch (NexusGetException e) {
            testException(e);
        }
   }

    @Test
    public void testXErrorBackend500() throws NexusGetException, NexusResourceNotFoundException {
        try {
            String url = "/mock/v1/dataError500";
            backendService.get(url, ERROR_RESPONSE_TYPE);
        } catch (NexusGetException e) {
            testException(e);
        }
    }

    @Test
    public void testXErrorBackend401() throws  NexusGetException, NexusResourceNotFoundException {
        try {
            String url = "/mock/v1/dataError401";
            backendService.get(url, ERROR_RESPONSE_TYPE);
        } catch (NexusGetException e) {
            testException(e);
        }
    }
}
