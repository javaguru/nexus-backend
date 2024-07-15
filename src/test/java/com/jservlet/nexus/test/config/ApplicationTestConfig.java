package com.jservlet.nexus.test.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

@Configuration
@ComponentScan(basePackages = {"com.jservlet.nexus.shared.service"})
public class ApplicationTestConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationTestConfig.class);

    @Bean
    public BackendService backendService(@Value("${nexus.backend.url}") String backendUrl,
                                         RestOperations restOperations,
                                         ObjectMapper objectMapper) {
        final BackendService backendService = new BackendServiceImpl();
        backendService.setBackendURL(backendUrl);
        backendService.setRestOperations(restOperations);
        backendService.setObjectMapper(objectMapper);
        return backendService;
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new Jackson2ObjectMapperBuilder()
                // fields not null globally! but not working !?
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // to allow serialization of "empty" POJOs (no properties to serialize)
                .failOnEmptyBeans(false)
                // to prevent exception when encountering unknown property:
                .failOnUnknownProperties(false)

                // to enable entries are first sorted by key before serialization
                .featuresToEnable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

                // to write java.util.Date, Calendar as number (timestamp):
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                //.featuresToDisable(SerializationFeature.WRITE_NULL_MAP_VALUES)

                // to allow coercion of JSON empty String ("") to null Object value:
                .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .featuresToEnable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)

                // disable, not thrown an exception if an unknown property
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                // activate ISO8601 dates !
                .dateFormat(new StdDateFormat().withColonInTimeZone(true))

                // to enable standard indentation ("pretty-printing"):
                .indentOutput(true)
                //.modules(jacksonModule())
                .build();
    }


    @Bean
    public RestOperations backendRestOperations(MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter) {
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory());
        mappingJackson2HttpMessageConverter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));
        restTemplate.setMessageConverters(Arrays.asList(
                new StringHttpMessageConverter(UTF_8),
                new FormHttpMessageConverter(),
                new ByteArrayHttpMessageConverter(),
                //new ResourceHttpMessageConverter(), // WARN mandatory or use HttpMessageConverter<Resource>
                new HttpMessageConverter<Resource>() {
                    @Override
                    public boolean canRead(@NonNull Class<?> clazz, MediaType mediaType) {
                        return Resource.class.isAssignableFrom(clazz);
                    }

                    @Override
                    public boolean canWrite(@NonNull Class<?> clazz, MediaType mediaType) {
                        return false;
                    }

                    @Override
                    public @NonNull List<MediaType> getSupportedMediaTypes() {
                        return Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM);
                    }

                    @Override
                    public @NonNull Resource read(@NonNull Class<? extends Resource> clazz, @NonNull HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
                        InputStream inputStream = inputMessage.getBody();
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(inputStream.available());
                        try (inputStream; outputStream) {
                            IOUtils.copy(inputStream, outputStream);
                        }

                        final byte[] content = outputStream.toByteArray();
                        return new AbstractResource() {
                            @Override
                            public @NonNull String getDescription() {
                                return "HttpInputMessage resource";
                            }

                            @Override
                            public @NonNull InputStream getInputStream() throws IOException {
                                return new ByteArrayInputStream(content);
                            }

                            @Override
                            public String getFilename() {
                                HttpHeaders httpHeaders = inputMessage.getHeaders();
                                String disposition = httpHeaders.getFirst(HttpHeaders.CONTENT_DISPOSITION);
                                if (disposition != null) {
                                    int filenameIdx = disposition.indexOf("filename");
                                    if (filenameIdx != -1) {
                                        return disposition.substring(filenameIdx + 9);
                                    }
                                }
                                return null;
                            }
                        };

                    }

                    @Override
                    public void write(@NonNull Resource resource, MediaType contentType, @NonNull HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

                    }
                },
                mappingJackson2HttpMessageConverter
        ));
        return restTemplate;
    }

    @Value("${nexus.backend.timeout:30}")
    private int readTimeout;

    @Value("${nexus.backend.max_connections_per_route:20}")
    private int defaultMaxConnectionsPerRoute;

    @Value("${nexus.backend.max_connections:30}")
    private int maxConnections;

    @Value("${nexus.backend.close_idle_connections_timeout:0}")
    private int closeIdleConnectionsTimeout;


    @Bean
    public ClientHttpRequestFactory httpRequestFactory() {

        DefaultConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                return super.getKeepAliveDuration(response, context);
            }
        };

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setDefaultMaxPerRoute(defaultMaxConnectionsPerRoute);
        cm.setMaxTotal(maxConnections);
        cm.setValidateAfterInactivity(2 * 1000); // 2s
        cm.closeIdleConnections(closeIdleConnectionsTimeout, TimeUnit.SECONDS);

        return new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(readTimeout * 1000)
                        .setConnectionRequestTimeout(readTimeout * 1000)
                        .setSocketTimeout(readTimeout * 1000)
                        //.setStaleConnectionCheckEnabled(true)
                        .setAuthenticationEnabled(true)
                        .build())
                .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
                .disableCookieManagement()
                .disableAuthCaching()
                .setKeepAliveStrategy(myStrategy)
                .disableConnectionState()
                .build());
    }

}

