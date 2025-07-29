package com.jservlet.nexus.test.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import com.jservlet.nexus.shared.web.interceptor.CookieRedirectInterceptor;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpConnectionFactory;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.IgnoreSpecProvider;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicLineParser;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.PrivateKeyDetails;
import org.apache.http.ssl.PrivateKeyStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.CharArrayBuffer;
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

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.Socket;
import java.security.KeyStore;
import java.util.*;
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
                },
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
    @Value("${nexus.backend.client.requestSentRetryEnabled:false}")
    private boolean requestSentRetryEnabled;

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

        final DefaultConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                return super.getKeepAliveDuration(response, context);
            }
        };

        final HttpConnectionFactory<HttpRoute, ManagedHttpClientConnection> connFactory =
                new ManagedHttpClientConnectionFactory(
                        new DefaultHttpRequestWriterFactory(),
                        new DefaultHttpResponseParserFactory(
                                new CompliantLineParser(), new DefaultHttpResponseFactory()));

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
                    .build(), connFactory);
        } else {
            cm = new PoolingHttpClientConnectionManager(connFactory);
        }

        cm.setDefaultMaxPerRoute(defaultMaxConnectionsPerRoute);
        cm.setMaxTotal(maxConnections);
        cm.setValidateAfterInactivity(validateAfterInactivity * 1000);
        cm.closeIdleConnections(closeIdleConnectionsTimeout, TimeUnit.SECONDS);

        return new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create()
                .setUserAgent(userAgent)
                .setConnectionManager(cm)
                .setDefaultCookieSpecRegistry(RegistryBuilder.<CookieSpecProvider>create()
                        .register(CookieSpecs.IGNORE_COOKIES, new IgnoreSpecProvider()) // Forced IGNORE_COOKIES for a Gateway stateless!
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(connectTimeout * 1000)
                        .setConnectionRequestTimeout(requestTimeout * 1000)
                        .setSocketTimeout(socketTimeout * 1000)
                        .setRedirectsEnabled(redirectsEnabled) // mandatory disabled by default!
                        .setMaxRedirects(maxRedirects)
                        .setAuthenticationEnabled(authenticationEnabled)
                        .setCircularRedirectsAllowed(circularRedirectsAllowed)
                        .build())
                .setConnectionReuseStrategy(new DefaultConnectionReuseStrategy())
                .setKeepAliveStrategy(myStrategy)
                .setRedirectStrategy(new LaxRedirectStrategy())
                .setRetryHandler(new DefaultHttpRequestRetryHandler(retryCount, requestSentRetryEnabled))
                .disableRedirectHandling()
                //.disableCookieManagement() // Cookie manually managed, the Gateway is Stateless!
                .disableAuthCaching()
                .disableConnectionState()
                .build());
    }

    /**
     * Force HttpClient into accepting malformed response heads in order to salvage the content of the messages.
     * (Deal non-standard and non-compliant behaviours!)
     */
    static class CompliantLineParser extends BasicLineParser {
        @Override
        public Header parseHeader(CharArrayBuffer buffer) throws ParseException {
            try {
                return super.parseHeader(buffer);
            } catch (ParseException ex) {
                // Suppress ParseException exception
                return new BasicHeader(buffer.toString(), null);
            }
        }
    }
}

