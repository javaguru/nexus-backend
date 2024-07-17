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

package com.jservlet.nexus.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.ErrorMessage;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.jservlet.nexus.config.web.WebConstants.*;

/*
 * The Mock Controller VM Options: -Denvironment=development
 */
@Controller
@Tag(name = "Mock", description = "Mock Application")
@RequestMapping("/mock")
public class MockController extends ApiBase {

    private static final Logger logger = LoggerFactory.getLogger(MockController.class);

    private static final String ENV_VAR = "environment";

    private Environment env;

    private static final String SOURCE = "MOCK-NEXUS-REST-BACKEND";

    private static final String fileName = "logo-marianne.svg";
    private static final String fileTest = System.getProperty("java.io.tmpdir") + fileName;

    @Autowired
    public ObjectMapper objectMapper;

    public MockController() {
        super(SOURCE);
    }

    @Autowired
    public void setEnv(Environment env) {
        this.env = env;
    }

    @GetMapping(path = {"", "/"})
    public String mock() {
        return "development".equals(env.getProperty(ENV_VAR)) ? "mock" : "error/error403";
    }

    @Operation(summary = "Get Binary data", description = "Get Binary data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = byte[].class))}),
    })
    @GetMapping(path = "/v1/dataBytes")//, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    public ResponseEntity<?> getBytes() {
        return new ResponseEntity<>("GET_BYTES".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
    }

    @Operation(summary = "Get a data", description = "Get a data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @GetMapping(path = "/v1/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get() {
        return new ResponseEntity<>(new Data("info1","info2","info3"), HttpStatus.OK);
    }

    @Operation(summary = "Post a data", description = "Post a data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @PostMapping(path = "/v1/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> post(@RequestBody Data data) {
        return new ResponseEntity<>(true, HttpStatus.OK);
    }

    @Operation(summary = "Put a data", description = "Put a data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @PutMapping(path = "/v1/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> put(@RequestBody Data data) {
        return new ResponseEntity<>(true, HttpStatus.OK);
    }

    @Operation(summary = "Get data Xss", description = "Get data Xss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @GetMapping(path = "/v1/dataXss", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getXss(@RequestParam String param1) {
        return new ResponseEntity<>(new Data(param1,"info2","info3", new Date()), HttpStatus.OK);
    }

    @Operation(summary = "Post data Xss", description = "Post data Xss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @PostMapping(path = "/v1/dataPostXss", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postXss(@RequestParam String param1, @RequestBody Data data) {
        return new ResponseEntity<>(new Data(param1, data.data2, data.data3, data.data4), HttpStatus.OK);

    }

    @Operation(summary = "Get data List", description = "Get data List")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data[].class))}),
    })
    @GetMapping(path = "/v1/dataList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getList() {
        List<Data> dataList = new ArrayList<>();
        dataList.add(new Data("info1","info2","info3"));
        dataList.add(new Data("info4","info5","info6"));
        return new ResponseEntity<>(dataList, HttpStatus.OK);
    }

    @Operation(summary = "Post data List", description = "Post data List")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data[].class))}),
    })
    @PostMapping(path = "/v1/dataList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postDataList() {
        List<Data> dataList = new ArrayList<>();
        dataList.add(new Data("info1","info2","info3"));
        dataList.add(new Data("info4","info5","info6"));
        return new ResponseEntity<>(dataList, HttpStatus.OK);
    }

    @Operation(summary = "Post datafile", description = "Post datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HttpStatus.class))}),
    })
    @PostMapping(path = "/v1/datafile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postFile(@RequestParam("file") MultipartFile file) throws IOException {
        FileUtils.copyInputStreamToFile(file.getInputStream(), new File(fileTest));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Operation(summary = "Put datafile", description = "Put datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HttpStatus.class))}),
    })
    @PutMapping(path = "/v1/datafile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> putFile(@RequestParam("file") MultipartFile file) throws IOException {
        FileUtils.copyInputStreamToFile(file.getInputStream(), new File(fileTest));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Operation(summary = "Delete datafile", description = "Delete datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HttpStatus.class))}),
    })
    @DeleteMapping(path = "/v1/datafile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteFile() {
        return new ResponseEntity<>(FileUtils.deleteQuietly(new File(fileTest)), HttpStatus.OK);
    }

    @Operation(summary = "Get datafile", description = "Get datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = byte[].class))}),
    })
    @GetMapping(path = "/v1/datafile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> getFile() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=" + new File(fileTest).getName().toLowerCase())
                .body(new InputStreamResource(new ClassPathResource(fileName).getInputStream()));
    }

    @Operation(summary = "Patch a data", description = "Patch a data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })

    @PatchMapping(path = "/v1/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patch(@RequestBody Data data) {
        return new ResponseEntity<>(true, HttpStatus.OK);
    }
    @Operation(summary = "Patch datafile", description = "Patch datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $204, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HttpStatus.class))}),
    })
    @PatchMapping(path = "/v1/datafile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patchFile(@RequestParam("file") MultipartFile file) throws IOException {
        FileUtils.copyInputStreamToFile(file.getInputStream(), new File(fileTest));
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Get Error 400", description = "Get Error 400")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $400, description = REQ_NOT_CORRECTLY, content = {@Content(schema = @Schema(implementation = ErrorMessage.class))}),
    })
    @GetMapping(path = "/v1/dataError400", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getError400() {
        return new ResponseEntity<>(new ErrorMessage("400", SOURCE,"Bad Request"), HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Get Error 401", description = "Get Error 401")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $401, description = USER_NOT_AUTH, content = {@Content(schema = @Schema(implementation = ErrorMessage.class))}),
    })
    @GetMapping(path = "/v1/dataError401", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getError401() {
        return new ResponseEntity<>(new ErrorMessage("401", SOURCE,"Unauthorized"), HttpStatus.UNAUTHORIZED);
    }

    @Operation(summary = "Get Error 500", description = "Get Error 500")
     @ApiResponses(value = {
              @ApiResponse(responseCode = $500, description = INTERNAL_SERVER, content = {@Content(schema = @Schema(implementation = ErrorMessage.class))}),
     })
    @GetMapping(path = "/v1/dataError500", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getError500() {
        return new ResponseEntity<>(new ErrorMessage("500", SOURCE,"Internal Server Error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /*
     * Proxy in ByteArray
     * The data can be retrieved as a byte[] is a direct proxy without any in-app data conversion.
     * (See WebMvcConfigurer.configureMessageConverters(List<HttpMessageConverter<?>> converters) need to be disabled)
     */
    @Operation(summary = "Proxy Post data ", description = "Proxy Post data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = byte[].class))}),
    })
    @PostMapping(value = "/v1/proxy", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE) //
    public ResponseEntity<byte[]> redirect(@RequestBody(required = false) String body, HttpServletRequest request) throws URISyntaxException {
        // Switch url /v1/proxy --> /v1/redirect
        String queryString = request.getQueryString();
        String url = request.getRequestURL().toString().replaceAll("/proxy", "/redirect") + "?" + (queryString !=null ? queryString : "");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url).replaceQuery(queryString);
        RestTemplate restTemplate = new RestTemplate();
        RequestEntity<String> req = new RequestEntity<>(body, extractHeaders(request), HttpMethod.POST, builder.build().toUri());
        try {
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(req, byte[].class);
            return new ResponseEntity<>(responseEntity.getBody(), filterHeaders(responseEntity.getHeaders()), responseEntity.getStatusCode());
        } catch (HttpClientErrorException e) {
            logger.info("Error occured during proxying: {}", e.getMessage());
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), filterHeaders(e.getResponseHeaders()), e.getStatusCode());
        }
    }
    /* Hidden Redirect endpoint */
    @Hidden
    @RequestMapping(value = "/v1/redirect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> redirect(@RequestBody(required = false) String body,
                                      HttpMethod method,
                                      HttpServletRequest request) {
        // Switch url /v1/redirect --> /v1/echo
        String queryString = request.getQueryString();
        String url = request.getRequestURL().toString().replaceAll("/redirect", "/echo") + "?" + (queryString !=null ? queryString : "");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url).replaceQuery(queryString);
        // WARN All is Byte Array! Or a Typed response is mandatory!
        // WARN An Object.class can only return a ResponseEntity<Object>, try /datList, and not a ResponseEntity<String> try /echo !?
        // WARN No suitable HttpMessageConverter found for response type [class java.lang.Object] and content type [text/plain;charset=ISO-8859-1]
        return new RestTemplate().exchange(builder.build().toUri(), method, new HttpEntity<>(body, extractHeaders(request)), byte[].class);
    }
    /* Hidden Echo endpoint */
    @Hidden
    @RequestMapping(path = "/v1/echo", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> echo(@RequestBody(required = false) String body, HttpMethod method, HttpServletRequest request)
            throws JsonProcessingException {
        Map<String, String[]> map = request.getParameterMap();
        Map<String, String[]> headers = extractHeaders(request).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray(new String[0])));
        String json = objectMapper.writeValueAsString(new EchoEntity(method.name(), body, map, headers));
        return new ResponseEntity<>(json, HttpStatus.OK);
    }

    private static class EchoEntity implements Serializable {
        public Map<String, String[]> headers;
        public Map<String, String[]> map;
        public String body;
        public String method;

        public EchoEntity(String method, String body, Map<String, String[]> map, Map<String, String[]> headers) {
            this.method = method;
            this.body = body;
            this.headers = headers;
            this.map = map;
        }
    }

    @Schema
    public static class Data {

        @Parameter(name = "data1", description = "data1 field")
        public String data1;
        @Parameter(name = "data2", description = "data2 field")
        public String data2;
        @Parameter(name = "data3", description = "data3 field")
        public String data3;
        @Parameter(name = "data4", description = "Date field")
        public Date data4;

        public Data() {
        }
        public Data(String data1, String data2, String data3) {
            this.data1 = data1;
            this.data2 = data2;
            this.data3 = data3;
        }
        public Data(String data1, String data2, String data3, Date data4) {
            this.data1 = data1;
            this.data2 = data2;
            this.data3 = data3;
            this.data4 = data4;
        }
        @Override
        public String toString() {
            return "Data{" +
                    "data1='" + data1 + '\'' +
                    ", data2='" + data2 + '\'' +
                    ", data3='" + data3 + '\'' +
                    ", data4='" + data4 + '\'' +
                    '}';
        }
    }

    private static HttpHeaders extractHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin(request.getRequestURL().toString());
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.add(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    private HttpHeaders filterHeaders(HttpHeaders originalHeaders) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(originalHeaders);
        //chuncked and length headers must not be forwarded
        responseHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
        responseHeaders.remove(HttpHeaders.CONTENT_LENGTH);
        //responseHeaders.remove("Accept-Encoding"); // Gzip !?
        //Following headers must not be included, because they would appear twice
        responseHeaders.remove(HttpHeaders.CACHE_CONTROL);
        responseHeaders.remove(HttpHeaders.PRAGMA);
        responseHeaders.remove(HttpHeaders.VARY);
        responseHeaders.remove(HttpHeaders.EXPIRES);
        responseHeaders.remove("X-Content-Type-Options");
        responseHeaders.remove("X-XSS-Protection");
        responseHeaders.remove("X-Frame-Options");
        return responseHeaders;
    }
}
