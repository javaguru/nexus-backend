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

package com.jservlet.nexus.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.jservlet.nexus.config.web.tomcat.ssl.TomcatConnectorConfig;
import com.jservlet.nexus.config.web.WebConfig;
import com.jservlet.nexus.shared.config.annotation.ConfigProperties;
import com.jservlet.nexus.config.web.WebSecurityConfig;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Socket;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *  Application Config Nexus Backend Application
 *  Loader ConfigProperties file "classpath:settings.properties"
 */
@Configuration
@ConfigProperties("classpath:settings.properties")
@Import({
        WebConfig.class,
        WebSecurityConfig.class,
        TomcatConnectorConfig.class
})
public class ApplicationConfig  {

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

    @Value("${nexus.backend.mapper.serializer.date:yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}") // or ZZ +00:00
    private String patternDate;
    @Value("${nexus.backend.mapper.serializer.timezone:}") // default locale timezone, or Etc/UTC or Europe/Paris
    private String patternDateTimeZone;

    @Value("${nexus.backend.mapper.date.timezone:Zulu}")
    private String timeZone;
    @Value("${nexus.backend.mapper.date.withColon:true}")
    private boolean withColonInTimeZone;

    @Value("${nexus.backend.mapper.indentOutput:true}")
    private boolean indentOutput;


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
                .dateFormat(new StdDateFormat().withTimeZone(TimeZone.getTimeZone(timeZone))
                        .withColonInTimeZone(withColonInTimeZone))
                //.dateFormat(new ISO8601DateFormat())

                // to enable standard indentation ("pretty-printing"):
                .indentOutput(indentOutput)
                .modules(jacksonModule())
                .build();
    }

    private Module jacksonModule() {
        final SimpleModule module = new SimpleModule();
        final SimpleDateFormat formatter = new SimpleDateFormat(patternDate);
        if (patternDateTimeZone != null && !patternDateTimeZone.isEmpty()) {
            formatter.setTimeZone(TimeZone.getTimeZone(patternDateTimeZone));
        }
        module.addSerializer(Date.class, new JsonSerializer<>() {
            @Override
            public void serialize (Date value, JsonGenerator gen, SerializerProvider unused) throws IOException {
                gen.writeString(formatter.format(value));
            }
        });
        module.addSerializer(Double.class, new JsonSerializer<>() {
            @Override

            public void serialize(Double value, JsonGenerator jgen, SerializerProvider unused) throws IOException {
                if (null == value) {
                    jgen.writeNull();
                } else {
                    if (value.isNaN() || value.isInfinite()) {
                        jgen.writeNumber(value);
                    } else if (value == 0d) {
                        jgen.writeNumber(0d);
                    } else {
                        jgen.writeNumber(BigDecimal.valueOf(value).setScale(9, RoundingMode.HALF_UP).toString());
                    }
                }
            }
        });
        return module;
    }

    @Bean
    public RestOperations backendRestOperations(MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter) throws Exception {
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory());
        // not MediaType.ALL only Json and Json wildcard!
        mappingJackson2HttpMessageConverter.setSupportedMediaTypes(
                Arrays.asList(
                        MediaType.APPLICATION_JSON,
                        new MediaType("application", "*+json"),
                        MediaType.APPLICATION_JSON_UTF8,
                        MediaType.APPLICATION_FORM_URLENCODED,
                        MediaType.APPLICATION_PROBLEM_JSON_UTF8
                )
        );

        restTemplate.setMessageConverters(Arrays.asList(
                new StringHttpMessageConverter(UTF_8),
                new FormHttpMessageConverter(),
                new ByteArrayHttpMessageConverter(),
                //new ResourceHttpMessageConverter(), // WARN or let the ResourceHttpRequestHandler create for us!
                mappingJackson2HttpMessageConverter
        ));
        return restTemplate;
    }


    @Value("${nexus.backend.client.timeout:30}")
    private int readTimeout;

    @Value("${nexus.backend.client.max_connections_per_route:20}")
    private int defaultMaxConnectionsPerRoute;

    @Value("${nexus.backend.client.max_connections:100}")
    private int maxConnections;

    @Value("${nexus.backend.client.close_idle_connections_timeout:0}")
    private int closeIdleConnectionsTimeout;

    @Value("${nexus.backend.client.validate_after_inactivity:2}")
    private int validateAfterInactivity;

    @Value("${nexus.backend.client.header.user-agent:JavaNexus}")
    private String userAgent;


    /**
     * Activated the Mutual Authentication or mTLS, default protocol TLSv1.3
     */
    @Value("${nexus.backend.client.ssl.mtls.enable:false}")
    private boolean isMTLS;

    @Value("${nexus.backend.client.ssl.key-store:nexus-default.jks}")
    private String pathJKS;
    @Value("${nexus.backend.client.ssl.key-store-password:changeit}")
    private String keyStorePassword;
    @Value("${nexus.backend.client.ssl.certificate.alias:key_server}")
    private String certificateAlias;
    /**
     * SpEL reads allow method delimited with a comma and splits into a List of Strings
     */
    @Value("#{'${nexus.backend.client.ssl.https.protocols:TLSv1.3}'.split(',')}")
    private List<String> httpsProtocols;
    @Value("#{'${nexus.backend.client.ssl.https.cipherSuites:TLS_AES_256_GCM_SHA384}'.split(',')}")
    private List<String> httpsCipherSuites;

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() throws Exception {

        DefaultConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                return super.getKeepAliveDuration(response, context);
            }
        };

        final PoolingHttpClientConnectionManager cm;

        if (isMTLS) {
            final KeyStore identityKeyStore = KeyStore.getInstance("jks");
            final FileInputStream identityKeyStoreFile = new FileInputStream(pathJKS);
            identityKeyStore.load(identityKeyStoreFile, keyStorePassword.toCharArray());

            final KeyStore trustKeyStore = KeyStore.getInstance("jks");
            final FileInputStream trustKeyStoreFile = new FileInputStream(pathJKS);
            trustKeyStore.load(trustKeyStoreFile, keyStorePassword.toCharArray());

            final SSLContext sslContext = SSLContexts.custom()
                    // load identity keystore
                    .loadKeyMaterial(identityKeyStore, keyStorePassword.toCharArray(), new PrivateKeyStrategy() {
                        @Override
                        public String chooseAlias(Map<String, PrivateKeyDetails> aliases, Socket socket) {
                            return certificateAlias;
                        }
                    })
                    // load trust keystore
                    .loadTrustMaterial(trustKeyStore, null)
                    .build();

            // WARN only protocol TLSv1.3, Not a mix with TLSv1.2,TLSv1.1 cause SSLSocket duplex close failed!!!
            // WARN only CipherSuites TLS_AES_256_GCM_SHA384 or TLS_AES_128_GCM_SHA256
            final SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    httpsProtocols.toArray(new String[0]),
                    httpsCipherSuites.toArray(new String[0]), // TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier());

            // WARN Not set a sslConnectionSocketFactory cause a HandshakeContext with a dummy KeyManager!!!
            cm = new PoolingHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslConnectionSocketFactory)
                    .build());
        } else {
            cm = new PoolingHttpClientConnectionManager();
        }

        cm.setDefaultMaxPerRoute(defaultMaxConnectionsPerRoute);
        cm.setMaxTotal(maxConnections);
        cm.setValidateAfterInactivity(validateAfterInactivity * 1000);
        cm.closeIdleConnections(closeIdleConnectionsTimeout, TimeUnit.SECONDS);

        return new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setConnectionManager(cm)
                .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(readTimeout * 1000)
                    .setConnectionRequestTimeout(readTimeout * 1000)
                    .setSocketTimeout(readTimeout * 1000)
                    .setAuthenticationEnabled(true)
                    .build())
                .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
                .setKeepAliveStrategy(myStrategy)
                .disableCookieManagement()
                .disableAuthCaching()
                .disableConnectionState()
                .disableAutomaticRetries()
                .build());
    }

}
