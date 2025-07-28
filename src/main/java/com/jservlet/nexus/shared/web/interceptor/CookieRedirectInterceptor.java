/*
 * Copyright (C) 2001-2025 JServlet.com Franck Andriano.
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CookieRedirect propagate Cookies receive during a redirection http status 3xx. without any alteration and Set-Cookie.
 */
public class CookieRedirectInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger Logger = LoggerFactory.getLogger(CookieRedirectInterceptor.class);

    private final int maxRedirects;

    public CookieRedirectInterceptor(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    @Override
    public @NonNull ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body, @NonNull ClientHttpRequestExecution execution) throws IOException {
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
                @Override @NonNull
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
            // Cookies fields: Version, Name, Path, Domain, Expires/MaxAge, Secure HttpOnly or SameSite.
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

            // Propagate Cookies receive without any alteration
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
        @Override @NonNull
        public String getMethodValue() { return method.toString();}
        @Override @NonNull
        public URI getURI() { return uri != null ? uri : original.getURI();}
        @Override @NonNull
        public HttpHeaders getHeaders() { return original.getHeaders();}

        public void setUri(URI uri) { this.uri = uri;}
        public void setMethod(HttpMethod method) { this.method = method;}
    }
}
