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

package com.jservlet.nexus.shared.web.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CookieRedirect propagate Cookies receive during a redirection http status 3xx. without any alteration and Set-Cookie.
 * Now includes support for Cookie Domain Rewriting, Secure flag stripping, and Cookie Jar deduplication.
 */
public class CookieRedirectInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CookieRedirectInterceptor.class);

    private final int maxRedirects;
    private final String rewriteDomain;
    private final boolean keepSecure;

    /**
     * @param maxRedirects Maximum number of redirects to follow
     */
    public CookieRedirectInterceptor(int maxRedirects) {
        this(maxRedirects, null, true);
    }

    /**
     * @param maxRedirects Maximum number of redirects to follow
     * @param rewriteDomain The domain to enforce on cookies.
     * If empty (""), the Domain attribute is stripped (best for localhost proxy).
     * If null, no rewriting is performed.
     * @param keepSecure    If false, strips the "Secure" and "SameSite=None" flags from cookies
     * (useful for local HTTP development). If true, leaves them as-is.
     */
    public CookieRedirectInterceptor(int maxRedirects, String rewriteDomain, boolean keepSecure) {
        this.maxRedirects = maxRedirects;
        this.rewriteDomain = rewriteDomain;
        this.keepSecure = keepSecure;
    }

    /**
     * The Interceptor
     * @param request   HttpRequest
     * @param body      byte[]
     * @param execution ClientHttpRequestExecution
     * @return ClientHttpResponse The Client Http Response
     * @throws IOException Exception
     */
    @Override
    public @NonNull ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
        Map<CookieIdentifier, String> cookieJar = new LinkedHashMap<>();
        return executeWithRedirectHandling(request, body, execution, cookieJar, 0);
    }

    private ClientHttpResponse executeWithRedirectHandling(HttpRequest request, byte[] body,
                                                           ClientHttpRequestExecution execution, Map<CookieIdentifier, String> cookieJar, int redirectCount) throws IOException {

        logger.debug("Execute request to " + request.getURI() + " (Redirect count: " + redirectCount + ")");

        ClientHttpResponse response = execution.execute(request, body);
        HttpStatusCode statusCode = response.getStatusCode();

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
            logger.debug("Interceptor: Redirecting from {} to {}", request.getURI(), redirectUri);

            // Get cookies redirection and Set-Cookie Cookie Jar
            List<String> setCookieHeaders = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            updateCookieJar(setCookieHeaders, cookieJar);

            // Prepare the new request with the Wrapper
            HttpRequest redirectedRequest = new HttpRequestWrapper(request) {
                @Override @NonNull
                public URI getURI() {
                    return redirectUri;
                }

                @Override @NonNull
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

            // Format and add headers Cookie for redirection request from the Cookie Jar
            if (!cookieJar.isEmpty()) {
                String cookieHeaderValue = cookieJar.values().stream()
                        .map(h -> h.split(";", 2)[0]) // Get key=value
                        .collect(Collectors.joining("; "));
                // Use set() force erasing duplicate Cookie
                redirectedRequest.getHeaders().set(HttpHeaders.COOKIE, cookieHeaderValue);
            } else {
                // Remove cookie in the previous request
                redirectedRequest.getHeaders().remove(HttpHeaders.COOKIE);
            }

            // Close previous response before redirection
            response.close();

            // Execute a redirect request by a recursive call
            return executeWithRedirectHandling(redirectedRequest, body, execution, cookieJar, redirectCount + 1);

        } else {
            // Wrap the final response to allow modification of its headers.
            MutableClientHttpResponse mutableResponse = new MutableClientHttpResponse(response);

            // Get final Set-Cookie headers and update the Cookie Jar
            List<String> finalSetCookies = mutableResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
            updateCookieJar(finalSetCookies, cookieJar);

            // Clear all existing Set-Cookie headers in the response to avoid duplicates
            mutableResponse.getHeaders().remove(HttpHeaders.SET_COOKIE);

            // Inject the clean, deduplicated, and rewritten cookies back into the response
            for (String cookieStr : cookieJar.values()) {
                String finalCookieToSet = applyDomainAndSecurityRewrite(cookieStr);
                if (finalCookieToSet != null) {
                    mutableResponse.getHeaders().add(HttpHeaders.SET_COOKIE, finalCookieToSet);
                }
            }

            return mutableResponse;
        }
    }

    /**
     * Parses Set-Cookie headers and updates the Cookie Jar, overwriting older values.
     */
    private void updateCookieJar(List<String> headers, Map<CookieIdentifier, String> cookieJar) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (String header : headers) {
            try {
                List<HttpCookie> parsedCookies = HttpCookie.parse(header);
                for (HttpCookie cookie : parsedCookies) {
                    CookieIdentifier id = new CookieIdentifier(cookie.getName(), cookie.getPath(), cookie.getDomain());
                    cookieJar.put(id, header);
                }
            } catch (IllegalArgumentException e) {
                logger.warn(String.format("Failed to parse Set-Cookie header '%s': %s", header, e.getMessage()));
            }
        }
    }

    /**
     * Applies Domain rewriting and Secure flag stripping logic using Regex.
     */
    private String applyDomainAndSecurityRewrite(String cookieHeader) {
        if (cookieHeader == null) {
            return null;
        }

        String rewrittenCookie = cookieHeader;

        // Handle Domain Rewriting
        if (this.rewriteDomain != null) {
            if (this.rewriteDomain.isEmpty()) {
                    // Strip the domain completely
                    rewrittenCookie = rewrittenCookie.replaceAll("(?i);\\s*Domain=[^;]+", "");
            } else {
                // Replace the domain with the specified rewriteDomain
                if (rewrittenCookie.matches("(?i).*;\\s*Domain=.*")) {
                    rewrittenCookie = rewrittenCookie.replaceAll("(?i)(;\\s*Domain=)[^;]+", "$1" + this.rewriteDomain);
                } else {
                    // If it didn't have a domain, add it
                    rewrittenCookie = rewrittenCookie + "; Domain=" + this.rewriteDomain;
                }
            }
        }

        // Handle Secure Flag Stripping (for local development)
        if (!this.keepSecure) {
            // Remove the 'Secure' flag
            rewrittenCookie = rewrittenCookie.replaceAll("(?i);\\s*Secure", "");
            // Browsers reject SameSite=None without Secure, so if we strip Secure, we must alter SameSite.
            // Downgrading to Lax is the safest fallback for cross-site local testing without HTTPS.
            rewrittenCookie = rewrittenCookie.replaceAll("(?i);\\s*SameSite=None", "; SameSite=Lax");
        }

        // Clean up any double semicolons created by the replacements
        rewrittenCookie = rewrittenCookie.replaceAll(";\\s*;", ";");

        return rewrittenCookie;
    }

    private static class CookieIdentifier {
        public final String name;
        public final String path;
        public final String domain;

        public CookieIdentifier(String name, String path, String domain) {
            // Cookie names are case-insensitive
            this.name = (name != null) ? name.toLowerCase(Locale.ENGLISH) : null;
            this.path = path;
            // Domain names are case-insensitive
            this.domain = (domain != null) ? domain.toLowerCase(Locale.ENGLISH) : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CookieIdentifier that = (CookieIdentifier) o;
            return Objects.equals(name, that.name) && Objects.equals(path, that.path) && Objects.equals(domain, that.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, path, domain);
        }
    }

    private static boolean isRedirect(HttpStatusCode status) {
        return status.is3xxRedirection() && status != HttpStatus.NOT_MODIFIED; // 304 is not a redirection to follow!
    }

    // Basic wrapper
    private static class HttpRequestWrapper implements HttpRequest {
        private final HttpRequest original;
        private URI uri;
        private HttpMethod method;

        HttpRequestWrapper(HttpRequest original) { this.original = original;}
        @Override @NonNull
        public HttpMethod getMethod() { return method != null ? method: original.getMethod();}
        @Override @NonNull
        public URI getURI() { return uri != null ? uri : original.getURI();}
        /*// SpringBoot 3.4.5 and Spring-Web 6.2.6
        @Override @NonNull
        public Map<String, Object> getAttributes() {
            return original.getAttributes();
        }*/
        @Override @NonNull
        public HttpHeaders getHeaders() { return original.getHeaders();}

        public void setUri(URI uri) { this.uri = uri;}
        public void setMethod(HttpMethod method) { this.method = method;}
    }

    /**
     * A wrapper around ClientHttpResponse that makes headers mutable.
     */
    private static class MutableClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse original;
        private final HttpHeaders headers;

        MutableClientHttpResponse(ClientHttpResponse original) {
            this.original = original;
            // SpringBoot 3.4.5 and Spring-Web 6.2.6
            //this.headers = new HttpHeaders(original.getHeaders());
            this.headers = HttpHeaders.writableHttpHeaders(original.getHeaders());
        }

        @Override @NonNull
        public HttpStatusCode getStatusCode() throws IOException {
            return original.getStatusCode();
        }

        @Override @NonNull
        public String getStatusText() throws IOException {
            return original.getStatusText();
        }

        @Override @NonNull
        public InputStream getBody() throws IOException {
            return original.getBody();
        }

        @Override @NonNull
        public HttpHeaders getHeaders() { return this.headers; }

        @Override
        public void close() { original.close(); }
    }
}
