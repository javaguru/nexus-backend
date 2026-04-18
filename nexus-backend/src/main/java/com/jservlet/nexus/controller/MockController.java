/*
 * Copyright (C) 2001-2026 JServlet.com Franck Andriano.
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static com.jservlet.nexus.config.web.WebConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * The Mock Controller VM Options: -Denvironment=development
 */
@Controller
@ConditionalOnExpression("'${environment}' == 'development'")
@Tag(name = "Mock", description = "Mock Application")
@RequestMapping("/mock")
public class MockController extends ApiBase {

    private static final Logger logger = LoggerFactory.getLogger(MockController.class);

    private static final String ENV_VAR = "environment";

    private final Environment env;

    private static final String SOURCE = "MOCK-REST-NEXUS-BACKEND";

    private static final String fileName = "logo-marianne.svg";
    private static final String fileResource = "/META-INF/resources/static/images/" + fileName;
    private static final String fileTest = System.getProperty("java.io.tmpdir") + fileName;

    public MockController(Environment env) {
        super(SOURCE);
        this.env = env;
    }

    @GetMapping(path = {"", "/"})
    public String mock() {
        return "development".equals(env.getProperty(ENV_VAR)) ? "mock" : "error/error403";
    }

    @Operation(summary = "Get ByteArray data", description = "Get ByteArray data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = byte[].class))}),
    })
    @GetMapping(path = "/v1/dataBytes")//, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    public ResponseEntity<?> getBytes() {
        return new ResponseEntity<>("GET_BYTES".getBytes(UTF_8), HttpStatus.OK);
    }

    @Operation(summary = "Get a data", description = "Get a data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @GetMapping(path = "/v1/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get() {
        return new ResponseEntity<>(new Data("info1","info2", 10.00), HttpStatus.OK);
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

    @Operation(summary = "Patch a data", description = "Patch a data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @PatchMapping(path = "/v1/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patch(@RequestBody Data data) {
        return new ResponseEntity<>(true, HttpStatus.OK);
    }

    /* Tests Xss */
    @Operation(summary = "Get data Xss", description = "Get data Xss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @GetMapping(path = "/v1/dataXss", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getXss(@RequestParam(value = "param1") String param1) {
        return new ResponseEntity<>(new Data(param1,"info2",10.05, new Date()), HttpStatus.OK);
    }

    @Operation(summary = "Post data Xss", description = "Post data Xss")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data.class))}),
    })
    @PostMapping(path = "/v1/dataPostXss", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postXss(@RequestParam(value = "param1") String param1, @RequestBody Data data) {
        return new ResponseEntity<>(new Data(param1, data.data2, data.data3, data.data4), HttpStatus.OK);

    }

    /* Data List */
    @Operation(summary = "Get data List", description = "Get data List")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data[].class))}),
    })
    @GetMapping(path = "/v1/dataList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getList() {
        List<Data> dataList = new ArrayList<>();
        dataList.add(new Data("info1","info2",10.00));
        dataList.add(new Data("info4","info5",10.05));
        return new ResponseEntity<>(dataList, HttpStatus.OK);
    }

    @Operation(summary = "Post and get data List", description = "Post and get data List")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data[].class))}),
    })
    @PostMapping(path = "/v1/dataList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postDataList() {
        List<Data> dataList = new ArrayList<>();
        dataList.add(new Data("info1","info2",10.00));
        dataList.add(new Data("info4","info5",0.0006));
        return new ResponseEntity<>(dataList, HttpStatus.OK);
    }
    @Operation(summary = "Post data List", description = "Post data List")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = Data[].class))}),
    })
    @PostMapping(path = "/v1/dataPostList", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> postDataList(@RequestBody List<Data> dataList) {
        return new ResponseEntity<>(true, HttpStatus.OK);
    }
    /* End Data List */


    /* Data File */
    @Operation(summary = "Get datafile", description = "Get datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = byte[].class))}),
    })
    @GetMapping(path = "/v1/datafile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> getFile() throws IOException {
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=" + new File(fileTest).getName().toLowerCase())
                .body(new InputStreamResource(new ClassPathResource(fileResource).getInputStream()));
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
    @Operation(summary = "Patch datafile", description = "Patch datafile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $204, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HttpStatus.class))}),
    })
    @PatchMapping(path = "/v1/datafile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patchFile(@RequestParam("file") MultipartFile file) throws IOException {
        FileUtils.copyInputStreamToFile(file.getInputStream(), new File(fileTest));
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
    /* End Data File */

    @Operation(summary = "Get Error 400", description = "Get Error 400")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $400, description = REQ_NOT_CORRECTLY, content = {@Content(schema = @Schema(implementation = ResponseEntity.class))}),
    })
    @GetMapping(path = "/v1/dataError400", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getError400() {
        return super.getResponseEntity("400", "ERROR", "Bad Request", HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Get Error 401", description = "Get Error 401")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $401, description = USER_NOT_AUTH, content = {@Content(schema = @Schema(implementation = Message.class))}),
    })
    @GetMapping(path = "/v1/dataError401", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getError401() {
        return super.getResponseEntity("401", "ERROR", "Unauthorized", HttpStatus.UNAUTHORIZED);
    }

    @Operation(summary = "Get Error 500", description = "Get Error 500")
     @ApiResponses(value = {
              @ApiResponse(responseCode = $500, description = INTERNAL_SERVER, content = {@Content(schema = @Schema(implementation = Message.class))}),
     })
    @GetMapping(path = "/v1/dataError500", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getError500() {
        return super.getResponseEntity("500", "ERROR", "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);
    }


    /*
     * Proxy in ByteArray
     * The data can be retrieved as a byte[] is a direct proxy without any in-app data conversion.
     * (See WebMvcConfigurer.configureMessageConverters(List<HttpMessageConverter<?>> converters) need to be disabled)
     */
    @Operation(summary = "Proxy data ", description = "Proxy data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = byte[].class))}),
    })
    @PostMapping(value = "/v1/proxy")
    public ResponseEntity<byte[]> proxy(@RequestBody(required = false) String body, HttpServletRequest request) throws URISyntaxException {
        // Switch url /v1/proxy --> /v1/redirect
        String url = request.getRequestURL().toString().replace("/proxy", "/redirect");
        // Filter headers before sending the request
        HttpHeaders headers = filterHeaders(extractHeaders(request));
        // Use the filtered headers
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<byte[]> responseEntity = new RestTemplate().exchange(url, HttpMethod.POST, entity, byte[].class);
            // Filter headers again before returning to the client
            return new ResponseEntity<>(responseEntity.getBody(), filterHeaders(responseEntity.getHeaders()), responseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            return new ResponseEntity<>(e.getResponseBodyAsByteArray(), filterHeaders(Objects.requireNonNull(e.getResponseHeaders())), e.getStatusCode());
        }
    }
    @GetMapping(value = "/v1/proxy")
    public ResponseEntity<byte[]> proxyGet(HttpServletRequest request) throws URISyntaxException {
        return this.proxy(null, request); // no body!
    }

    /* Hidden Redirect endpoint */
    @Hidden
    @RequestMapping(value = "/v1/redirect")
    public ResponseEntity<?> redirect(@RequestBody(required = false) String body,
                                      HttpServletRequest request) {
        // Switch url /v1/redirect --> /v1/echo
        String url = request.getRequestURL().toString().replace("/redirect", "/echo");
        URI uri = UriComponentsBuilder.fromHttpUrl(url).build().toUri();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        return new RestTemplate().exchange(uri, method, new HttpEntity<>(body, extractHeaders(request)), byte[].class); // All is Bytes!

    }
    /* Hidden Echo endpoint */
    @Hidden
    @RequestMapping(path = "/v1/echo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> echo(@RequestBody(required = false) String body, HttpServletRequest request)
            throws JsonProcessingException {
        Map<String, String[]> map = request.getParameterMap();
        Map<String, String[]> headers = extractHeaders(request).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toArray(new String[0])));
        return new ResponseEntity<>(new EchoEntity(request.getMethod(), body, map, headers), HttpStatus.OK);
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

    private HttpHeaders filterHeaders(HttpHeaders headers) {
        HttpHeaders filtered = new HttpHeaders();
        headers.forEach((name, values) -> {
            // Remove headers that RestTemplate or the Web Server should calculate
            if (!name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) &&
                    !name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) &&
                    !name.equalsIgnoreCase(HttpHeaders.CONNECTION)) {
                filtered.addAll(name, values);
            }
        });
        return filtered;
    }

    @Schema
    public static class EchoEntity implements Serializable {
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
        public double data3;

        @Parameter(name = "data4", description = "Date field")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        public Date data4;

        public Data() {
        }
        public Data(String data1, String data2, double data3) {
            this.data1 = data1;
            this.data2 = data2;
            this.data3 = data3;
        }
        public Data(String data1, String data2, double data3, Date data4) {
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


    /**
     * VULNERABILITY Personal John Doe
     */
    @Operation(summary = "Get Personal Joe ", description = "Get data Joe List")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = DataJoe.class))}),
    })
    @GetMapping(path = "/v1/dataJoe", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getListJoe() {
        DataJoe dataJoe = new DataJoe();
        HashMap<String, Object> hashJoe = new HashMap<>();
        hashJoe.put("userid", 123);
        hashJoe.put("name", "John Doe");
        hashJoe.put("email", "john.doe@example.com");
        hashJoe.put("phone", "+1-555-123-4567");
        hashJoe.put("address", "123 Fake Street");
        dataJoe.setDataJoe(hashJoe);
        return new ResponseEntity<>(dataJoe, HttpStatus.OK);
    }
    @Operation(summary = "Get a data Joe2", description = "Get a data Joe2")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HashMap.class))}),
    })
    @GetMapping(path = "/v1/dataJoe2", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDataJoe2() {
        HashMap<String, Object> hashJoe = new HashMap<>();
        hashJoe.put("user_id", 123);
        hashJoe.put("name", "John Doe");
        hashJoe.put("age", 25);
        hashJoe.put("akia_key", "AKIAQWERTYUIOPASDF");

        return new ResponseEntity<>(hashJoe, HttpStatus.OK);
    }
    @Operation(summary = "Get a data Joe2", description = "Get a data Joe2")
    @ApiResponses(value = {
            @ApiResponse(responseCode = $200, description = REQ_SUCCESSFULLY, content = {@Content(schema = @Schema(implementation = HashMap.class))}),
    })
    @GetMapping(path = "/v1/dataJoe3", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDataJoe3() {
        HashMap<String, Object> dataJoe = new HashMap<>(); // no user_id!
        dataJoe.put("name", "Joe");
        dataJoe.put("age", 25);
        dataJoe.put("AKIAQWERTYUIOPASDF", "AKIAQWERTYUIOPASDF");
        return new ResponseEntity<>(dataJoe, HttpStatus.OK);
    }
    @Operation(summary = "Get Financial Data Joe", description = "Returns mock financial data including IBAN and Credit Card numbers.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful", content = {@Content(schema = @Schema(implementation = HashMap.class))}),
    })
    @GetMapping(path = "/v1/financialDataJoe", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getFinancialData() {
        HashMap<String, Object> hashJoe = new HashMap<>();
        hashJoe.put("transaction_id", "TXN-12345");
        hashJoe.put("beneficiary_iban", "00:1A:2B:3C:4D:5E");
        hashJoe.put("payment_method", "credit_card");
        hashJoe.put("card_number", "4971 1234 5678 9010");
        return new ResponseEntity<>(hashJoe, HttpStatus.OK);
    }
    @Operation(summary = "Get Identity Data", description = "Returns mock identity data including Passport and SSN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful", content = {@Content(schema = @Schema(implementation = HashMap.class))}),
    })
    @GetMapping(path = "/v1/identityData", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getIdentityData() {
        List<Data> dataList = new ArrayList<>();
        dataList.add(new Data("user_name", "Jane Doe", 0.0));
        dataList.add(new Data("passport_number", "AB123456", 0.0));
        dataList.add(new Data("ssn", "123-45-6789", 0.0));
        return new ResponseEntity<>(dataList, HttpStatus.OK);
    }
    @Operation(summary = "Get Technical Data", description = "Returns mock technical data with IP and MAC addresses.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful", content = {@Content(schema = @Schema(implementation = HashMap.class))}),
    })
    @GetMapping(path = "/v1/techData", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getTechData() {
        HashMap<String, Object> hashJoe = new HashMap<>();
        hashJoe.put("last_login_ip", "8.8.8.8");
        hashJoe.put("device_mac_address", "00:1A:2B:3C:4D:5E");
        return new ResponseEntity<>(hashJoe, HttpStatus.OK);
    }
    @Operation(summary = "Get Vehicle Data", description = "Returns mock vehicle data with French and US license plates.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful", content = {@Content(schema = @Schema(implementation = HashMap.class))}),
    })
    @GetMapping(path = "/v1/vehiculeData", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getVehicleData() {
        HashMap<String, Object> hashJoe = new HashMap<>();
        hashJoe.put("vehicule_fr", "Peugeot 208");
        hashJoe.put("license_plate_fr", "AB-123-CD");
        hashJoe.put("vehicle_us", "ABC 1234");
        hashJoe.put("license_plate_us", "ABC 1234");
        return new ResponseEntity<>(hashJoe, HttpStatus.OK);
    }

    private static final Map<String, UserProfile> userDatabase = new HashMap<>();
    static {
        userDatabase.put("122", new UserProfile("122", "Bob", "bob@example.com", "Secret de Bob"));
        userDatabase.put("123", new UserProfile("123", "Alice", "alice@example.com", "Secret d'Alice"));
        userDatabase.put("124", new UserProfile("124", "Joe", "joe@example.com", "Secret de Joe"));
        userDatabase.put("125", new UserProfile("125", "Tina", "tina@example.com", "Secret de Joe"));
    }
    // BOLA
    @Operation(summary = "Get User Profile by ID (BOLA Vulnerable)", description = "Get a user's profile by their ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful", content = {@Content(schema = @Schema(implementation = UserProfile.class))}),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping(path = "/v1/users/{userId}/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getUserProfile(@PathVariable(value = "userId") String userId) {
        // No check to see if the authenticated user is the same as the requested userId!
        UserProfile user = userDatabase.get(userId);
        if (user != null) {
            return new ResponseEntity<>(user, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }
    }
    //BFLA
    @Operation(summary = "Delete a user by ID (BFLA Vulnerable)", description = "Deletes a user from the system. This endpoint should be restricted to administrators.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(path = "/v1/admin/users/{userId}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteUser(@PathVariable(value = "userId") String userId) {
        // VULNERABILITY: Check to see if the user is admin
        if (userDatabase.containsKey(userId)) {
            userDatabase.remove(userId);
            return new ResponseEntity<>("User " + userId + " deleted.", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }
    }
    @Operation(summary = "Get Application Configuration (BFLA Vulnerable)", description = "Returns sensitive application configuration details. Should be admin-only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful")
    })
    @GetMapping(path = "/v1/admin/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAppConfig() {
        // VULNERABILITY: No role check. A normal user can access this.
        Map<String, String> config = new HashMap<>();
        config.put("database.url", "jdbc:postgresql://prod-db.internal:5432/main");
        config.put("payment.gateway.apikey", "sk_live_xxxxxxxxxxxxxx"); // Sensitive data!
        config.put("log.level", "DEBUG");

        return new ResponseEntity<>(config, HttpStatus.OK);
    }
    @Operation(summary = "Trigger Cache Refresh (BFLA Vulnerable)", description = "Forces a refresh of the application's internal cache. Should be admin-only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Cache refresh initiated")
    })
    @PostMapping(path = "/v1/admin/cache/refresh")
    public ResponseEntity<?> refreshCache() {
        // VULNERABILITY: No role check. A normal user can trigger this task.
        logger.info("INFO: Admin task 'refreshCache' triggered by user.");
        // Refresh cache !?
        return new ResponseEntity<>("Cache refresh initiated.", HttpStatus.ACCEPTED);
    }
    @Operation(summary = "Update User Status (BFLA Vulnerable)", description = "Updates a user's status. Should be admin-only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PutMapping(path = "/v1/admin/users/{userId}/status")
    public ResponseEntity<?> setUserStatus(@PathVariable(value = "userId") String userId, @RequestBody Map<String, String> status) {
        // No role check.
        if (userDatabase.containsKey(userId)) {
            logger.info("INFO: User " + userId + " status changed to: " + status.get("status"));
            return new ResponseEntity<>("Status updated for user " + userId, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("User not found.", HttpStatus.NOT_FOUND);
        }
    }
    @Operation(summary = "Update current user profile (BOPLA Vulnerable)", description = "Updates the profile of the current user. Allows for mass assignment.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully", content = {@Content(schema = @Schema(implementation = User.class))}),
    })
    @PutMapping(path = "/v1/users/me", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateUser(@RequestBody User updatedUserData) {
        // VULNERABILITY: Mass Assignment.
        currentUser.setUsername(updatedUserData.getUsername());
        currentUser.setEmail(updatedUserData.getEmail());
        // add "role": "admin" in the JSON for an elevation of privileges!
        if (updatedUserData.getRole() != null) {
            currentUser.setRole(updatedUserData.getRole());
        }
        logger.info("INFO: User updated. New role: " + currentUser.getRole());
        return new ResponseEntity<>(currentUser, HttpStatus.OK);
    }
    @Operation(summary = "Get a list of products (URC Vulnerable)", description = "Returns a list of products with unsafe pagination.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request successful", content = {@Content(array = @ArraySchema(schema = @Schema(implementation = Product.class)))}),
    })
    @GetMapping(path = "/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getProducts(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        // VULNERABILITY: No validation is performed on the 'limit' parameter.
        // An attacker can provide a very large value (e.g., 999999) to trigger a DoS.
        List<Product> productList = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            productList.add(new Product("prod-" + i, "Product Name " + i, 19.99));
        }
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return new ResponseEntity<>(productList, HttpStatus.OK);
    }
    // Joe Product
    static class Product {
        public String productId;
        public String name;
        public double price;
        public Product() {}
        public Product(String productId, String name, double price) {
            this.productId = productId;
            this.name = name;
            this.price = price;
        }
    }
    private static final User currentUser = new User("testuser", "test@example.com");
    // Joe User
    @Schema
    public static class User {
        public String username;
        public String email;
        public String role = "user";

        public User() {}

        public User(String username, String email) {
            this.username = username;
            this.email = email;
        }
        // Getters et Setters pour la sérialisation JSON
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
    // Joe profile
    @Schema
    static class UserProfile {
        public String userId;
        public String name;
        public String email;
        public String secretInfo;

        public UserProfile(String userId, String name, String email, String secretInfo) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.secretInfo = secretInfo;
        }
    }

    @Schema
    public static class DataJoe {

        @Parameter(name = "dataJoe", description = "dataJoe field")
        public Map dataJoe ;

        public DataJoe() {
        }
        public DataJoe(Map dataJoe, String data2, double data3) {
            this.dataJoe = dataJoe;
        }
        @Override
        public String toString() {
            return "Data{" +
                    "dataJoe='" + dataJoe + '\'' +
                    '}';
        }

        public Map getDataJoe() {
            return dataJoe;
        }

        public void setDataJoe(Map dataJoe) {
            this.dataJoe = dataJoe;
        }
    }
    /* end John Doe */



}
