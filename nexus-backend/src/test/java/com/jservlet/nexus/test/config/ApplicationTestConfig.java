package com.jservlet.nexus.test.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import com.jservlet.nexus.shared.web.interceptor.CookieRedirectInterceptor;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.DefaultConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.PrivateKeyStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
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

import javax.net.ssl.SSLContext;
import java.io.*;
import java.security.KeyStore;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

@Configuration
public class ApplicationTestConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationTestConfig.class);

    @Bean
    public BackendService backendService(@Value("${nexus.backend.url}") String backendUrl,
                                         RestOperations restOperations,
                                         ObjectMapper objectMapper) {
        final BackendServiceImpl backendService = new BackendServiceImpl();
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
                .indentOutput(true)
                //.modules(jacksonModule())
                .build();
    }

    @Bean
    public RestOperations backendRestOperations(MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter) throws Exception {
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory());
        restTemplate.setInterceptors(List.of(new CookieRedirectInterceptor(maxRedirects)));

        // No DefaultUriBuilderFactory! Let display the security policy violation...

        // MediaType.ALL now! Json + Json wildcard, pdf, gif etc...
        mappingJackson2HttpMessageConverter.setSupportedMediaTypes(List.of(MediaType.ALL));

        restTemplate.setMessageConverters(Arrays.asList(
                new StringHttpMessageConverter(UTF_8),
                new FormHttpMessageConverter(),
                new ByteArrayHttpMessageConverter(),
                new ResourceHttpMessageConverter(), // WARN mandatory or use HttpMessageConverter<Resource> or
                /*new HttpMessageConverter<Resource>() {
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
                        return List.of(MediaType.APPLICATION_OCTET_STREAM);
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
                                return inputMessage.getHeaders().getContentDisposition().getFilename();
                            }
                        };
                    }

                    @Override
                    public void write(@NonNull Resource resource, MediaType contentType, @NonNull HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

                    }
                },*/
                mappingJackson2HttpMessageConverter
        ));
        return restTemplate;
    }

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

    @Value("${nexus.backend.client.redirectsEnabled:true}")
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

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() throws Exception {

        // 1. Stratégie Keep-Alive (Retourne un TimeValue en V5)
        final DefaultConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
                return super.getKeepAliveDuration(response, context);
            }
        };

        // 2. Configuration du Connection Manager
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

            // Stratégie de clé privée HC5
            PrivateKeyStrategy privateKeyStrategy = (aliases, sslParameters) -> certificateAlias;

            final SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(identityKeyStore, keyStorePassword.toCharArray(), privateKeyStrategy)
                    .loadTrustMaterial(trustKeyStore, null)
                    .build();

            // HC5 utilise un Builder pour le SSL Socket Factory
            final SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setTlsVersions(httpsProtocols.toArray(new String[0]))
                    .setCiphers(httpsCipherSuites.toArray(new String[0]))
                    .build();

            cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
        } else {
            cm = PoolingHttpClientConnectionManagerBuilder.create().build();
        }

        // 3. Configuration des connexions (Les timeouts de connexion sont ici maintenant !)
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(connectTimeout))
                .setValidateAfterInactivity(TimeValue.ofSeconds(validateAfterInactivity))
                .setTimeToLive(TimeValue.ofSeconds(closeIdleConnectionsTimeout))
                .build();

        cm.setDefaultConnectionConfig(connectionConfig);
        cm.setMaxTotal(maxConnections);
        cm.setDefaultMaxPerRoute(defaultMaxConnectionsPerRoute);

        // 4. Configuration de la requête (RequestConfig s'allège dans HC5)
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.STRICT)
                .setConnectionRequestTimeout(Timeout.ofSeconds(requestTimeout))
                .setResponseTimeout(Timeout.ofSeconds(socketTimeout)) // socketTimeout est renommé en responseTimeout
                .setRedirectsEnabled(redirectsEnabled)
                .setMaxRedirects(maxRedirects)
                .setAuthenticationEnabled(authenticationEnabled)
                .setCircularRedirectsAllowed(circularRedirectsAllowed)
                .build();

        // 5. Assemblage final du HttpClient
        HttpClient httpClient = HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .setKeepAliveStrategy(myStrategy)
                // RetryHandler devient RetryStrategy (on précise le délai entre les retries, ex: 1 sec)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(retryCount, TimeValue.ofSeconds(1)))
                .disableRedirectHandling()
                .disableAuthCaching()
                .disableConnectionState()
                .build();

        // 6. On wrap le tout pour Spring !
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }


}
