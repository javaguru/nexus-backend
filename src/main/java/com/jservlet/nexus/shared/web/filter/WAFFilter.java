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

package com.jservlet.nexus.shared.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

import javax.annotation.PostConstruct;
import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * WebFilter class implements a secure WAF protection for request Body.<br>
 * Http request Cookies, Headers, Parameters and Body can be filtered.
 * <p>
 * Un-normalized requests are automatically rejected by the StrictHttpFirewall,
 * and path parameters and duplicate slashes are removed for matching purposes.<br>
 * Noted the valid characters are defined in RFC 7230 and RFC 3986 are checked
 * by the Apache Coyote http11 processor (see coyote Error parsing HTTP request header)<br>
 * <p>
 * Default reactive mode is STRICT mode
 * <ul>
 * <li>STRICT:  StrictHttpFirewall + Rejects requests with malicious patterns.</li>
 * <li>PASSIVE: StrictHttpFirewall + Cleans malicious patterns from request body and parameters.</li>
 * <li>UNSAFE:  StrictHttpFirewall + No checks on request body.</li>
 * </ul>
 * <p>
 * Activated WebFilter by only 'nexus.api.backend.filter.waf.enabled=true' in the configuration
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.filter.waf.enabled")
public class WAFFilter extends ApiBase implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(WAFFilter.class);

    private static final String SOURCE = "INTERNAL-REST-NEXUS-BACKEND";

    /**
     * Defines the operational mode of the WAF.
     */
    public enum Reactive {
        STRICT,  // Rejects requests with malicious patterns.
        PASSIVE, // Cleans malicious patterns from the request.
        UNSAFE   // Performs no checks on the request body.
    }

    @Value("${nexus.api.backend.filter.waf.reactive.mode:STRICT}")
    private Reactive reactiveMode;

    @Value("${nexus.api.backend.filter.waf.deepscan.cookie:false}")
    private boolean isDeepScanCookie;

    private final WAFPredicate wafPredicate;
    private final ObjectMapper objectMapper;

    // Set xml MimeTypes
    private final Set<String> xmlMimeTypes = new HashSet<>();

    // Map magic numbers binaries files
    private final Map<String, String> magicNumbers = new HashMap<>();

    public WAFFilter(WAFPredicate wafPredicate, ObjectMapper objectMapper) {
        super(SOURCE);
        this.wafPredicate = wafPredicate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void postConstruct() {
        // Init XML MimeTypes
        xmlMimeTypes.add("image/svg+xml");
        xmlMimeTypes.add("text/xml");
        xmlMimeTypes.add("application/xml");
        xmlMimeTypes.add("application/vnd.mozilla.xul+xml");

        // Init magic numbers
        magicNumbers.put("FFD8FF", "image/jpeg");
        magicNumbers.put("47494638", "image/gif");
        magicNumbers.put("89504E470D0A1A0A", "image/png");
        magicNumbers.put("424D", "image/bmp");
        magicNumbers.put("FFFB", "audio/mpeg");
        magicNumbers.put("494433", "audio/mpeg");
        magicNumbers.put("52494646", "audio/wav");
        magicNumbers.put("255044462D", "application/pdf");
        magicNumbers.put("504B0304", "application/zip");
        magicNumbers.put("1F8B", "application/gzip");
        magicNumbers.put("7B5C727466", "application/rtf");
        magicNumbers.put("47", "video/mp2t");
        magicNumbers.put("1A45DFA3", "video/webm");
        magicNumbers.put("49492A00", "image/tiff");
        magicNumbers.put("4D4D002A", "image/tiff");
        magicNumbers.put("377A", "application/x-7z-compressed");
        magicNumbers.put("526172211A0700", "application/vnd.rar");
    }

    @Override
    public void init(FilterConfig filterConfig) {
        logger.info("Starting WAF Filter with reactive mode: {}", reactiveMode);
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;

        try {
            if (reactiveMode == Reactive.UNSAFE) { // WARN UNSAFE mode bypasses all checks.
                chain.doFilter(req, resp);
                return;
            }

            // Wrap the request to allow reading input stream multiple times and modifying parameters
            WAFRequestWrapper wrappedRequest = new WAFRequestWrapper(req);

            // Validate User-Agent
            validateUserAgent(wrappedRequest);

            // Wrap the request MultipartFiles
            HttpServletRequest processedRequest = scanAndWrapMultipartFiles(wrappedRequest);

            if (reactiveMode == Reactive.STRICT) {
                handleStrict(processedRequest);
            } else if (reactiveMode == Reactive.PASSIVE) {
                handlePassive(processedRequest);
            }

            // Continue the filter chain with the (potentially wrapped) request.
            chain.doFilter(processedRequest, response);

        } catch (RequestRejectedException ex) {
            handleRequestRejected(ex, req, resp);
        }
    }

    /**
     * Scans and wraps multipart files. Reads the file content into memory to
     * avoid FileNotFoundException in subsequent filters or controllers.
     *
     * @param request The current HttpServletRequest.
     * @return A new HttpServletRequest wrapper with processed multipart files.
     * @throws IOException  IO error
     */
    private HttpServletRequest scanAndWrapMultipartFiles(HttpServletRequest request) throws IOException {
        if (request instanceof MultipartRequest) {
            MultipartRequest originalMultipartRequest = (MultipartRequest) request;
            MultiValueMap<String, MultipartFile> newMultipartFiles = new LinkedMultiValueMap<>();

            for (Map.Entry<String, List<MultipartFile>> entry : originalMultipartRequest.getMultiFileMap().entrySet()) {
                for (MultipartFile file : entry.getValue()) {
                    if (file.isEmpty()) {
                        continue;
                    }
                    // Check magic numbers and file type
                    byte[] fileContent = file.getBytes();
                    String declaredContentType = file.getContentType();

                    if (fileContent.length > 0) {
                        // read magic numbers 8 octets!
                        byte[] header = Arrays.copyOfRange(fileContent, 0, Math.min(fileContent.length, 8));
                        String hexHeader = bytesToHex(header);
                        logger.debug("File '{}' with Content-Type '{}' has magic number: {}", file.getOriginalFilename(), declaredContentType, hexHeader);

                        String magicMimeType = null;
                        for (Map.Entry<String, String> magicEntry : magicNumbers.entrySet()) {
                            if (hexHeader.toUpperCase().startsWith(magicEntry.getKey())) {
                                magicMimeType = magicEntry.getValue();
                                break;
                            }
                        }

                        // magic numbers not corresponding to the Content-Type
                        if (magicMimeType != null && !magicMimeType.equals(file.getContentType())) {
                            throw new RequestRejectedException("Request rejected: Mime-type spoofing detected for file '" + file.getOriginalFilename() + "'.");
                        }
                    }

                    // Check XML files
                    if (declaredContentType != null && xmlMimeTypes.contains(declaredContentType.toLowerCase())) {
                        String content = new String(fileContent, StandardCharsets.UTF_8);
                        logger.debug("Scanning XML file: '{}' with Content-Type: '{}' for malicious patterns.", file.getOriginalFilename(), declaredContentType);

                        if (!wafPredicate.getWAFParameterValues().test(content)) {
                            throw new RequestRejectedException("Request rejected: Disallowed pattern found in XML file '" + file.getOriginalFilename() + "'.");
                        }
                    }

                    // Create a MultipartFile in memory for the next filter or controller
                    MultipartFile newFile = new WAFTempMultipartFile(file.getName(), file.getOriginalFilename(), declaredContentType, fileContent);
                    newMultipartFiles.add(entry.getKey(), newFile);
                }
            }
            return new WAFMultipartRequestWrapper(request, newMultipartFiles);
        }
        return request;
    }

    /**
     * Handles STRICT mode. Rejects requests on pattern match.
     *
     * @param request The processed HttpServletRequest.
     */
    private void handleStrict(HttpServletRequest request) throws IOException {
        // Check cookies if deep scan is enabled.
        if (isDeepScanCookie) {
            validateCookies(request);
        }

        // Check the request body for malicious patterns.
        String body = IOUtils.toString(request.getReader());
        if (!StringUtils.isBlank(body)) {
            rejectBodyIfInvalid(body);
        }
    }

    /**
     * Handles PASSIVE mode. Cleans the request on pattern match.
     *
     * @param request The processed HttpServletRequest.
     */
    private void handlePassive(HttpServletRequest request) throws IOException {
        // Clean XSS patterns from request parameters.
        Map<String, String[]> cleanedParameters = cleanParameterMap(request.getParameterMap());
        ((WAFRequestWrapper) request).setParameterMap(cleanedParameters);

        // Clean XSS patterns from the request body.
        String body = IOUtils.toString(request.getReader());
        if (!StringUtils.isBlank(body)) {
            String cleanedBody = stripWAFPatterns(body, wafPredicate.getXSSPatterns());
            ((WAFRequestWrapper) request).setInputStream(cleanedBody.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Converts a byte array to a hexadecimal string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    /**
     * Validates all cookies in the request if deep scan is enabled.
     */
    private void validateCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length > 100) {
                throw new RequestRejectedException("Request rejected: Exceeded cookie limit (100).");
            }
            for (Cookie cookie : cookies) {
                rejectCookieIfInvalid(cookie);
            }
        }
    }

    /**
     * Validates the User-Agent header if configured to do so.
     */
    private void validateUserAgent(HttpServletRequest request) {
        if (wafPredicate.isBlockDisallowedUserAgents()) {
            String userAgent = request.getHeader("User-Agent");
            if (wafPredicate.isUserAgentBlocked(userAgent)) {
                throw new RequestRejectedException("Request rejected: Disallowed User-Agent.");
            }
        }
    }

    /**
     * Centralized handler for sending a rejection response.
     */
    private void handleRequestRejected(RequestRejectedException ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        logger.error("Intercepted RequestRejectedException: {} RemoteHost: {} RequestURL: {} {} UserAgent: {}",
                LogFormatUtils.formatValue(ex.getMessage(), !logger.isDebugEnabled()), // No truncated in debug mode!
                req.getRemoteHost(), req.getMethod(), req.getServletPath(), req.getHeader("User-Agent"));

        resp.setStatus(HttpStatus.BAD_REQUEST.value());
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // Write a standard error message to the response body.
        byte[] responseBody = objectMapper.writeValueAsBytes(
                new Message("400", "ERROR", SOURCE, "Request rejected due to security policy violation.")
        );
        resp.getOutputStream().write(responseBody);
        resp.flushBuffer();
    }

    /**
     * Cleans malicious patterns from all values in a parameter map.
     * @return A new map with cleaned parameter values.
     */
    private Map<String, String[]> cleanParameterMap(Map<String, String[]> originalParameters) {
        Map<String, String[]> cleanedParameters = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : originalParameters.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            String[] cleanedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanedValues[i] = stripWAFPatterns(values[i], wafPredicate.getXSSPatterns());
            }
            cleanedParameters.put(key, cleanedValues);
        }
        return cleanedParameters;
    }

    /**
     * Removes all occurrences of the given patterns from a string.
     */
    private String stripWAFPatterns(String value, List<Pattern> patterns) {
        if (value == null) return null;
        // Prevent Regular Expression Denial of Service (ReDoS) attacks.
        if (value.length() > 100000) {
            throw new RequestRejectedException("Input value is too long for pattern stripping.");
        }
        String strippedValue = value;
        for (Pattern pattern : patterns) {
            strippedValue = pattern.matcher(strippedValue).replaceAll("");
        }
        return strippedValue;
    }

    /**
     * Throws RequestRejectedException if the request body contains disallowed patterns.
     */
    private void rejectBodyIfInvalid(String body) {
        if (!wafPredicate.getWAFParameterValues().test(body)) {
            throw new RequestRejectedException("Request rejected: Disallowed pattern found in request body.");
        }
    }

    /**
     * Throws RequestRejectedException if a cookie is invalid.
     */
    private void rejectCookieIfInvalid(Cookie cookie) {
        // Enforce HttpOnly flag for security.
        if (!cookie.isHttpOnly()) {
            throw new RequestRejectedException("Request rejected: Cookie '" + cookie.getName() + "' is not HttpOnly.");
        }
        // Validate cookie name against patterns.
        if (!wafPredicate.getWAFParameterNames().test(cookie.getName())) {
            throw new RequestRejectedException("Request rejected: Disallowed pattern in cookie name '" + cookie.getName() + "'.");
        }
        // Validate cookie value against patterns.
        if (!wafPredicate.getWAFParameterValues().test(cookie.getValue())) {
            throw new RequestRejectedException("Request rejected: Disallowed pattern in value for cookie '" + cookie.getName() + "'.");
        }
    }

    @Override
    public void destroy() {
        logger.info("Shutting down WAF Filter.");
    }

    /**
     * Class encapsulate a multipart request
     */
    @SuppressWarnings("unused")
    private static class WAFMultipartRequestWrapper extends WAFRequestWrapper {
        private final MultiValueMap<String, MultipartFile> multipartFiles;

        public WAFMultipartRequestWrapper(HttpServletRequest request, MultiValueMap<String, MultipartFile> multipartFiles) {
            super(request);
            this.multipartFiles = multipartFiles;
        }

        @Override
        public String getContentType() {
            return MediaType.MULTIPART_FORM_DATA_VALUE;
        }

        public MultipartRequest getNativeRequest(Class<MultipartRequest> requiredType) {
            return (MultipartRequest) this;
        }

        public MultiValueMap<String, MultipartFile> getMultiFileMap() {
            return this.multipartFiles;
        }

        public MultipartFile getFile(String name) {
            return this.multipartFiles.getFirst(name);
        }

        public List<MultipartFile> getFiles(String name) {
            return this.multipartFiles.get(name);
        }

        public Map<String, List<MultipartFile>> getFileMap() {
            return this.multipartFiles;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            // including map parameters
            Map<String, String[]> parameterMap = super.getParameterMap();
            for (Map.Entry<String, List<MultipartFile>> entry : multipartFiles.entrySet()) {
                if (!parameterMap.containsKey(entry.getKey())) {
                    parameterMap.put(entry.getKey(), new String[0]);
                }
            }
            return parameterMap;
        }

        @Override
        public String getParameter(String name) {
            String[] values = getParameterMap().get(name);
            if (values == null || values.length == 0) {
                return null;
            }
            return values[0];
        }

        @Override
        public String[] getParameterValues(String name) {
            return getParameterMap().get(name);
        }

        @Override
        public java.util.Enumeration<String> getParameterNames() {
            return Collections.enumeration(getParameterMap().keySet());
        }

    }

    /**
     * Class MultipartFile in memory
     */
     private static class WAFTempMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public WAFTempMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public @NonNull String getName() {
            return this.name;
        }

        @Override
        public String getOriginalFilename() {
            return this.originalFilename;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public boolean isEmpty() {
            return this.content == null || this.content.length == 0;
        }

        @Override
        public long getSize() {
            return this.content.length;
        }

        @Override
        public @NonNull byte[] getBytes() throws IOException {
            return this.content;
        }

        @Override
        public @NonNull InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(this.content);
        }

        @Override
        public void transferTo(@NonNull java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("This is an in-memory MultipartFile, cannot be transferred to a file.");
        }
    }
}
