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
import com.fasterxml.jackson.databind.*;
import com.jservlet.nexus.config.web.WebConfig;
import com.jservlet.nexus.config.web.WebSecurityConfig;
import com.jservlet.nexus.config.web.tomcat.ssl.TomcatConnectorConfig;
import com.jservlet.nexus.shared.config.annotation.ConfigProperties;
import com.jservlet.nexus.shared.service.backend.BackendService;
import com.jservlet.nexus.shared.service.backend.BackendServiceImpl;
import com.jservlet.nexus.shared.web.controller.api.ApiBackend;
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
import org.apache.http.impl.client.*;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.*;
import org.springframework.web.util.DefaultUriBuilderFactory;

import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.HttpCookie;
import java.net.Socket;
import java.net.URI;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Value("${nexus.backend.mapper.indentOutput:false}")
    private boolean indentOutput;


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
        restTemplate.setInterceptors(List.of(new CookieRedirectInterceptor(maxRedirects)));

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

    /**
     * Propagated Cookies and Set-Cookie during a redirection http status 3xx.
     */
    static class CookieRedirectInterceptor implements ClientHttpRequestInterceptor {

        private static final Logger Logger = LoggerFactory.getLogger(CookieRedirectInterceptor.class);

        private final int maxRedirects;

        public CookieRedirectInterceptor(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            List<String> setCookies = new ArrayList<>();
            return executeWithRedirectHandling(request, body, execution, setCookies, 0);
        }

        private ClientHttpResponse executeWithRedirectHandling(HttpRequest request, byte[] body,
                                                               ClientHttpRequestExecution execution, List<String> setCookies, int redirectCount) throws IOException {

            Logger.debug("Execute request to " + request.getURI() + " (Redirect count: " + redirectCount + ")");

            ClientHttpResponse response = execution.execute(request, body);
            HttpStatus statusCode = response.getStatusCode();

            // All 3xx redirection, exception 304 is not a redirection to follow!
            if (isRedirect(statusCode)) {
                if (redirectCount >= maxRedirects) {
                    throw new IOException("Too many redirections (" + redirectCount + ")");
                }

                // Get Location
                URI location = response.getHeaders().getLocation();
                if (location == null) {
                    throw new IOException("Redirect response " + statusCode + " for " + request.getURI() + " missing Location header");
                }

                // Correctly resolve the location URI against the current request URI
                URI redirectUri = request.getURI().resolve(location);
                Logger.debug("Interceptor: Redirecting from {} to {}", request.getURI(), redirectUri);

                // Get Set-Cookie
                List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);

                // Prepare the new request with the Wrapper
                HttpRequest redirectedRequest = new HttpRequestWrapper(request) {
                    @Override
                    public URI getURI() {
                        return redirectUri;
                    }

                    @Override
                    public HttpMethod getMethod() {
                        // 303 See Other force GET
                        if (statusCode == HttpStatus.SEE_OTHER) {
                            return HttpMethod.GET;
                        }
                        return super.getMethod(); // Keep original method for 301, 302, 307
                    }
                };

                // Delete specifics headers previous requested if need it (ex: Content-Length if GET)
                if (statusCode == HttpStatus.SEE_OTHER) {
                    redirectedRequest.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                    redirectedRequest.getHeaders().remove(HttpHeaders.CONTENT_TYPE);
                    body = new byte[0]; // Body empty for GET
                }

                // Format and add headers Cookie for redirection request
                if (setCookieHeaders != null && !setCookieHeaders.isEmpty()) {
                    setCookies.addAll(setCookieHeaders);
                    String cookieHeaderValue = setCookieHeaders.stream()
                            .map(h -> h.split(";", 2)[0]) // Get key=value
                            .collect(Collectors.joining("; "));
                    redirectedRequest.getHeaders().add(HttpHeaders.COOKIE, cookieHeaderValue);
                } else {
                    // Remove cookie in the previous request
                    redirectedRequest.getHeaders().remove(HttpHeaders.COOKIE);
                }

                // Close previous response before redirection
                response.close();

                // Execute a redirect request by a recursive call
                return executeWithRedirectHandling(redirectedRequest, body, execution, setCookies, redirectCount + 1);

            } else {
                // Get final Set-Cookie
                List<String> finalSetCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);

                // Identify the cookies to be propagated
                Set<CookieIdentifier> cookieIdentifiers = new HashSet<>();
                if (finalSetCookies != null) {
                    for (String existingHeader : finalSetCookies) {
                        try {
                            List<HttpCookie> parsedCookies = HttpCookie.parse(existingHeader);
                            for (HttpCookie cookie : parsedCookies) {
                                cookieIdentifiers.add(new CookieIdentifier(cookie.getName(), cookie.getPath()));
                            }
                        } catch (IllegalArgumentException e) {
                            Logger.warn(String.format("Failed to parse existing Set-Cookie header '%s': %s", existingHeader, e.getMessage()));
                        }
                    }
                }

                // Propagate Cookies receive without any alteration (Version, Name, Path, Domain, Expires/MaxAge, Secure HttpOnly or SameSite)
                for (String cookieHeader : setCookies) {
                    if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
                        continue;
                    }

                    List<HttpCookie> parsedCookies;
                    try {
                        parsedCookies = HttpCookie.parse(cookieHeader);
                        if (parsedCookies.isEmpty()) {
                            Logger.warn(String.format("Candidate Set-Cookie header '%s' parsed into zero cookies.", cookieHeader));
                            continue;
                        }
                    } catch (IllegalArgumentException e) {
                        Logger.warn(String.format("Failed to parse candidate Set-Cookie header '%s': %s", cookieHeader, e.getMessage()));
                        continue; // Skip malformed candidate header
                    }

                    // Match cookie candidate
                    boolean foundMatch = false;
                    for (HttpCookie cookie : parsedCookies) {
                        CookieIdentifier id = new CookieIdentifier(cookie.getName(), cookie.getPath());
                        if (cookieIdentifiers.contains(id)) {
                            foundMatch = true;
                            break;
                        }
                    }

                    // Propagate ?
                    if (!foundMatch) {
                        response.getHeaders().add(HttpHeaders.SET_COOKIE, cookieHeader);
                    }
                }

                return response;
            }
        }

        private static class CookieIdentifier {
            public final String name;
            public final String path;

            public CookieIdentifier(String name, String path) {
                this.name = (name != null) ? name.toLowerCase() : null;
                this.path = path;
            }
        }

        private static boolean isRedirect(HttpStatus status) {
            return status.is3xxRedirection() && status != HttpStatus.NOT_MODIFIED; // 304 is not a redirection to follow!
        }

        // Basic wrapper
        private static class HttpRequestWrapper implements HttpRequest {
            private final HttpRequest original;
            private URI uri;
            private HttpMethod method;

            HttpRequestWrapper(HttpRequest original) { this.original = original;}
            @Override
            public HttpMethod getMethod() { return method != null ? method: original.getMethod();}
            @Override
            public String getMethodValue() { return method.toString();}
            @Override
            public URI getURI() { return uri != null ? uri : original.getURI();}
            @Override
            public HttpHeaders getHeaders() { return original.getHeaders();}

            public void setUri(URI uri) { this.uri = uri;}
            public void setMethod(HttpMethod method) { this.method = method;}
        }
    }

}
