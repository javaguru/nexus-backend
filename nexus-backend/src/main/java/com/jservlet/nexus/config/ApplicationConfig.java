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

package com.jservlet.nexus.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.config.web.WebConfig;
import com.jservlet.nexus.config.web.WebSecurityConfig;
import com.jservlet.nexus.config.web.tomcat.ssl.TomcatConnectorConfig;
import com.jservlet.nexus.shared.config.annotation.ConfigProperties;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import com.jservlet.nexus.shared.web.controller.api.ApiBackend;
import com.jservlet.nexus.shared.web.interceptor.CookieRedirectInterceptor;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.PrivateKeyStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
@EnableConfigurationProperties(ApiBackend.ResourceMatchersConfig.class)
public class ApplicationConfig  {

    /**
     * Client Http5
     */
    @Value("${nexus.backend.client.connectTimeout:10}")
    private int connectTimeout;
    @Value("${nexus.backend.client.requestTimeout:20}")
    private int requestTimeout;
    @Value("${nexus.backend.client.socketTimeout:10}")
    private int socketTimeout;

    @Value("${nexus.backend.client.max_connections_per_route:20}")
    private int defaultMaxConnectionsPerRoute;

    @Value("${nexus.backend.client.max_connections:100}")
    private int maxConnections;

    @Value("${nexus.backend.client.close_idle_connections_timeout:0}")
    private int closeIdleConnectionsTimeout;

    @Value("${nexus.backend.client.validate_after_inactivity:2}")
    private int validateAfterInactivity;

    @Value("${nexus.backend.client.retryCount:3}")
    private int retryCount;

    @Value("${nexus.backend.client.redirectsEnabled:false}")
    private boolean redirectsEnabled;
    @Value("${nexus.backend.client.maxRedirects:5}")
    private int maxRedirects;
    @Value("${nexus.backend.client.authenticationEnabled:false}")
    private boolean authenticationEnabled;
    @Value("${nexus.backend.client.circularRedirectsAllowed:false}")
    private boolean circularRedirectsAllowed;


    /**
     * User-Agent
     */
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

    /**
     * Config verify-hostname
     */
    @Value("${nexus.backend.client.ssl.verify-hostname:false}")
    private boolean verifyHostname;

    /**
     * Config CookieRedirect allow rewrite Cookie Domain and Secure flag
     */
    @Value("${nexus.backend.client.cookie.domain:}") // empty (localhost) or #{null} (original domain)
    private String cookieDomain;
    @Value("${nexus.backend.client.cookie.secure:false}") // remove secure!
    private boolean cookieSecure;


    @Value("${nexus.backend.mapper.indentOutput:false}")
    private boolean indentOutput;

    /**
     * The Backend Service
     *
     * @param backendUrl        The Backend Url
     * @param restOperations    RestOperations
     * @param objectMapper      ObjectMapper
     * @return BackendService
     */
    @Bean
    public BackendService backendService(@Value("${nexus.backend.url}") String backendUrl,
                                         RestOperations restOperations,
                                         ObjectMapper objectMapper) {
        final BackendServiceImpl backendService = new BackendServiceImpl(true); // return a Generics Object!
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
                // fields not null globally!
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // to allow serialization of "empty" POJOs (no properties to serialize)
                .failOnEmptyBeans(false)
                // to prevent exception when encountering unknown property:
                .failOnUnknownProperties(false)

                // disable, not thrown an exception if an unknown property
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

                // to enable standard indentation ("pretty-printing"):
                .indentOutput(indentOutput)
                .build();
    }

    @Bean
    public RestOperations backendRestOperations(MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter,
                                                ClientHttpRequestFactory httpRequestFactory) throws Exception {

        RestTemplate restTemplate = new RestTemplate(httpRequestFactory);
        restTemplate.setInterceptors(List.of(new CookieRedirectInterceptor(maxRedirects, cookieDomain, cookieSecure)));

        // Does not encode the URI template, prevent to re-encode again the Uri with percent encoded in %25
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory();
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        restTemplate.setUriTemplateHandler(uriFactory);

        // MediaType.ALL now! Json + Json wildcard, pdf, gif etc...
        mappingJackson2HttpMessageConverter.setSupportedMediaTypes(List.of(MediaType.ALL));

        // List HttpMessage Converters
        restTemplate.setMessageConverters(Arrays.asList(
                new StringHttpMessageConverter(UTF_8),  // String
                new FormHttpMessageConverter(),      // Form x-www-form-urlencoded, multipart/form-data multipart/mixed
                new ByteArrayHttpMessageConverter(), // byte[] octet-stream
                new ResourceHttpMessageConverter(),  // Resource, ByteArrayResource
                mappingJackson2HttpMessageConverter  // JSON
        ));
        return restTemplate;
    }

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() throws Exception {
        // Strategy Keep-Alive
        final DefaultConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
                return super.getKeepAliveDuration(response, context);
            }
        };

        // Configuration Connection Manager
        PoolingHttpClientConnectionManager cm;

        if (isMTLS) {
            final KeyStore identityKeyStore = KeyStore.getInstance("jks");
            try (FileInputStream identityKeyStoreFile = new FileInputStream(pathJKS)) {
                identityKeyStore.load(identityKeyStoreFile, keyStorePassword.toCharArray());
            }

            final KeyStore trustKeyStore = KeyStore.getInstance("jks");
            try (FileInputStream trustKeyStoreFile = new FileInputStream(pathJKS)) {
                trustKeyStore.load(trustKeyStoreFile, keyStorePassword.toCharArray());
            }

            // Strategy private key
            PrivateKeyStrategy privateKeyStrategy = (aliases, sslParameters) -> certificateAlias;

            final SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(identityKeyStore, keyStorePassword.toCharArray(), privateKeyStrategy)
                    .loadTrustMaterial(trustKeyStore, null)
                    .build();

            // Builder SSL Socket Factory
            final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setTlsVersions(httpsProtocols.toArray(new String[0]))
                    .setCiphers(httpsCipherSuites.toArray(new String[0]))
                    .build();

            cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
        } else {
           if (!verifyHostname) {
               // Option Dev/Test
               final SSLConnectionSocketFactory devSslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                        .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                        .build();

                cm = PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(devSslSocketFactory)
                        .build();
            } else {
                // PROD HostnameVerifier strict
                cm = PoolingHttpClientConnectionManagerBuilder.create().build();
            }
        }

        // Configuration connections (connections timeouts here now!)
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(connectTimeout))
                .setValidateAfterInactivity(TimeValue.ofSeconds(validateAfterInactivity))
                .setTimeToLive(TimeValue.ofSeconds(closeIdleConnectionsTimeout))
                .build();

        cm.setDefaultConnectionConfig(connectionConfig);
        cm.setMaxTotal(maxConnections);
        cm.setDefaultMaxPerRoute(defaultMaxConnectionsPerRoute);

        // Configuration requester
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.STRICT)
                .setConnectionRequestTimeout(Timeout.ofSeconds(requestTimeout))
                .setResponseTimeout(Timeout.ofSeconds(socketTimeout)) // socketTimeout renamed responseTimeout
                .setRedirectsEnabled(redirectsEnabled)
                .setMaxRedirects(maxRedirects)
                .setAuthenticationEnabled(authenticationEnabled)
                .setCircularRedirectsAllowed(circularRedirectsAllowed)
                .build();

        // Final HttpClient5
        HttpClient httpClient = HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                // Management Request isolated CookieStore
                .setDefaultCookieStore(new RequestScopedCookieStore())
                .setKeepAliveStrategy(myStrategy)
                // RetryHandler become RetryStrategy (Retry delay, ex: 1 sec)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(retryCount, TimeValue.ofSeconds(1)))
                .disableRedirectHandling()
                // Cookie client stateful managed, the Gateway is Stateless! Session state is temporary and only affects the transaction!
                //.disableCookieManagement()
                .disableAuthCaching()
                .disableConnectionState()
                .build();

        // Wrap HttpComponents
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * Request-Isolated CookieStore use RequestContextHolder Thread-Safe.
     * Guarantees each incoming Spring MVC request get its own CookieStore, prevent session bleeding
     */
    public static class RequestScopedCookieStore implements CookieStore {
        private static final String COOKIE_STORE_ATTR = "SCOPED_COOKIE_STORE";
        private static final ThreadLocal<CookieStore> FALLBACK_STORE = ThreadLocal.withInitial(BasicCookieStore::new);

        private CookieStore getStore() {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            // Fallback to a temporary store if executed outside a web request (e.g., background thread)
            if (attributes == null) return FALLBACK_STORE.get();
            CookieStore store = (CookieStore) attributes.getAttribute(COOKIE_STORE_ATTR, RequestAttributes.SCOPE_REQUEST);
            if (store == null)  {
                store = new BasicCookieStore();
                attributes.setAttribute(COOKIE_STORE_ATTR, store, RequestAttributes.SCOPE_REQUEST);
            }
            return store;
        }

        @Override public void addCookie(Cookie cookie) { getStore().addCookie(cookie); }
        @Override public List<Cookie> getCookies() { return getStore().getCookies(); }
        @Override @Deprecated public boolean clearExpired(Date date) { return getStore().clearExpired(date); }
        @Override public boolean clearExpired(Instant date) { return getStore().clearExpired(date); }
        @Override public void clear() { getStore().clear(); }
    }

}
