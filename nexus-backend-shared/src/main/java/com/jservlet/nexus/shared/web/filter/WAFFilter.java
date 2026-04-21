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

package com.jservlet.nexus.shared.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import com.jservlet.nexus.shared.service.security.ml.RequestAnalyzerService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequestWrapper;
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

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
 * <li>STRICT_ONNX_AI: WebHttpFirewall + STRICT mode + Neural Network AI Scan.</li>
 * <li>ONNX_AI: WebHttpFirewall + Neural Network AI Scan.</li>
 * <li>STRICT:  WebHttpFirewall + Rejects requests with malicious patterns.</li>
 * <li>PASSIVE: WebHttpFirewall + Cleans malicious patterns from request body and parameters.</li>
 * <li>UNSAFE:  WebHttpFirewall + No checks on request body.</li>
 * </ul>
 * <p>
 * Activated WebFilter by only 'nexus.api.backend.filter.waf.enabled=true' in the configuration
 */
@Component
@ConditionalOnProperty(value = "nexus.api.backend.filter.waf.enabled")
public class WAFFilter extends ApiBase implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(WAFFilter.class);

    private static final String SOURCE = "INTERNAL-WAF-NEXUS-BACKEND";

    public enum Reactive {
        STRICT_ONNX_AI, // STRICT mode + Artificial Intelligence Scan by ONNX Neural Network
        ONNX_AI, // Artificial Intelligence Scan by ONNX Neural Network
        STRICT,  // Rejects requests with malicious patterns.
        PASSIVE, // Cleans malicious patterns from the request.
        UNSAFE   // Performs no checks on the request.
    }

    @Value("${nexus.api.backend.filter.waf.reactive.mode:STRICT_ONNX_AI}")
    private Reactive reactiveMode;

    @Value("${nexus.api.backend.filter.waf.deepscan.cookie:false}")
    private boolean isDeepScanCookie;

    // Max WAF file scan limit (ex: 15MB) to prevent RAM OutOfMemory (DoS attack)
    @Value("${nexus.api.backend.filter.waf.maxInMemoryFileSize:15728640}")
    private long maxInMemoryFileSize = 15 * 1024 * 1024;

    private final RequestAnalyzerService mlAnalyzer;
    private final WAFPredicate wafPredicate;
    private final ObjectMapper objectMapper;

    // Set xml MimeTypes
    private final Set<String> xmlMimeTypes = new HashSet<>();

    // Map magic numbers binaries files
    private final Map<String, String> magicNumbers = new HashMap<>();

    public WAFFilter(RequestAnalyzerService mlAnalyzer, WAFPredicate wafPredicate, ObjectMapper objectMapper) {
        super(SOURCE);
        this.mlAnalyzer = mlAnalyzer;
        this.wafPredicate = wafPredicate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void postConstruct() {
        xmlMimeTypes.addAll(Arrays.asList("image/svg+xml", "text/xml", "application/xml", "application/vnd.mozilla.xul+xml"));

        magicNumbers.put("FFD8FF", "image/jpeg");
        magicNumbers.put("47494638", "image/gif");
        magicNumbers.put("89504E47", "image/png"); // Simplified PNG magic number for matching
        magicNumbers.put("424D", "image/bmp");
        magicNumbers.put("255044462D", "application/pdf");
        magicNumbers.put("504B0304", "application/zip"); // Also DOCX / ODT
    }

    @Override
    public void init(FilterConfig filterConfig) {
        logger.info("Starting WAF Filter with reactive mode: {}", reactiveMode);
    }

    private final StandardServletMultipartResolver multipartResolver = new StandardServletMultipartResolver();

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
         HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse resp = (HttpServletResponse) response;

        try {
            if (reactiveMode == Reactive.UNSAFE) { // WARN UNSAFE mode bypasses all checks.
                chain.doFilter(req, resp);
                return;
            }

            if (multipartResolver.isMultipart(req)) {
                req = multipartResolver.resolveMultipart(req); // forced MultipartRequest!
            }

            // Wrap the request to allow reading input stream multiple times and modifying parameters
            WAFRequestWrapper wrappedRequest = new WAFRequestWrapper(req);

            // Validate Hostname & User-Agent (Gateways must check Host headers)
            validateHostAndUserAgent(wrappedRequest);

            // Wrap & Scan Multipart Files safely
            HttpServletRequest processedRequest = scanAndWrapMultipartFiles(wrappedRequest);

            // Bypass AI and WAF for root path, static files, and Swagger documentation
            String uri = processedRequest.getRequestURI().toLowerCase();
            String contextPath = processedRequest.getContextPath().toLowerCase();
            boolean isRootPath = uri.equals(contextPath) || uri.equals(contextPath + "/");

            if (isRootPath ||
                    uri.matches(".*\\.(html|htm|css|js|png|jpg|jpeg|ico|woff|woff2|ttf)$") || // svg|
                    uri.contains("/swagger-ui") ||
                    uri.contains("/v3/api-docs") ||
                    uri.contains("/swagger-resources")) {
                chain.doFilter(processedRequest, response);
                return;
            }

            // Apply WAF Policies (STRICT applies to both STRICT and PERFORM_AI)
            if (reactiveMode == Reactive.STRICT || reactiveMode == Reactive.STRICT_ONNX_AI) {
                handleStrict(processedRequest);
            } else if (reactiveMode == Reactive.PASSIVE) { // WARN PASSIVE mode can be dangerous.
                handlePassive(processedRequest);
            }

            // Bypass AI internal call controllers
            /*String remoteIp = request.getRemoteAddr();
            if ("127.0.0.1".equals(remoteIp) || "0:0:0:0:0:0:0:1".equals(remoteIp)) {
                chain.doFilter(request, response);
                return;
            }*/

            // Apply AI Scan ONLY if mode is ONNX_AI and STRICT checks passed
            if (reactiveMode == Reactive.ONNX_AI || reactiveMode == Reactive.STRICT_ONNX_AI) {
                handleAiScan(processedRequest);
            }

            // Continue the filter chain with the (potentially wrapped) request.
            chain.doFilter(processedRequest, response);

        } catch (RequestRejectedException ex) {
            handleRequestRejected(ex, req, resp);
        }
    }

    /**
     * Handles ONNX_AI mode. Uses Machine Learning to classify the entire payload.
     * Extracts Headers, Parameters, and Body with strict semantic tags for DistilBERT.
     *
     * @param request The processed HttpServletRequest.
     */
    private void handleAiScan(HttpServletRequest request) throws IOException {
        StringBuilder aiPayload = new StringBuilder();

        // Context-Agnostic Request Line (With STRICT AI Semantic Tags)
        String method = request.getMethod();
        String fullUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String agnosticUri = fullUri;

        if (contextPath != null && !contextPath.isEmpty() && fullUri.startsWith(contextPath)) {
            agnosticUri = fullUri.substring(contextPath.length());
        }
        if (agnosticUri.isEmpty()) {
            agnosticUri = "/";
        }

        // Add parameter to URI (?param=value) because the Python model learned them that way.
        // car le modèle Python les a appris ainsi.
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            agnosticUri += "?" + queryString;
        }

        // The exact tags expected by the Transformer
        aiPayload.append("METHOD: ").append(method).append("\n");
        aiPayload.append("URI: ").append(agnosticUri).append("\n");
        aiPayload.append("HEADERS:\n"); // <- CRUCIAL

        // Extract ONLY Security-Relevant Headers
        List<String> relevantHeaders = Arrays.asList("user-agent", "content-type", "cookie", "referer");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement().toLowerCase();
            if (relevantHeaders.contains(headerName)) {
                String headerValue = request.getHeader(headerName);

                if ("cookie".equals(headerName) && headerValue.length() > 200) {
                    headerValue = headerValue.substring(0, 200);
                }

                String displayHeader = headerName.substring(0, 1).toUpperCase() + headerName.substring(1);
                if (headerName.equals("user-agent")) displayHeader = "User-Agent";
                if (headerName.equals("content-type")) displayHeader = "Content-Type";

                aiPayload.append(displayHeader).append(": ").append(headerValue).append("\n");
            }
        }

        // Extract JSON/XML Body & Form Parameters
        StringBuilder bodyBuilder = new StringBuilder();

        // Added Form URL-Encoded parameters (only for requests with a body)
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                // URL parameters are ignored if they have already been included in the URI.
                if (queryString == null || !queryString.contains(entry.getKey() + "=")) {
                    bodyBuilder.append(entry.getKey()).append("=").append(String.join(",", entry.getValue())).append("\n");
                }
            }
        }

        // Extract JSON/XML Body
        String contentType = request.getContentType();

        // IA NLP not read the binary file...
        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            bodyBuilder.append("[MULTIPART_FILE_UPLOAD_SKIPPED]");
        } else {
            // Extraction Body
            String body = IOUtils.toString(request.getReader());
            if (StringUtils.isNotBlank(body)) {
                String flatBody = body.replaceAll("\\r\\n|\\r|\\n", " ").replaceAll("\\s+", " ").trim();
                bodyBuilder.append(flatBody);
            }
        }

        String finalBody = bodyBuilder.toString().trim();

        // Injection of the BODY tag (CRUCIAL for chunking)
        if (!finalBody.isEmpty()) {
            aiPayload.append("\nBODY:\n").append(finalBody);
        }

        String payloadToAnalyze = aiPayload.toString().trim();

        if (payloadToAnalyze.isEmpty()) {
            return; // Nothing to scan
        }

        if (payloadToAnalyze.length() > 5000) {
            payloadToAnalyze = payloadToAnalyze.substring(0, 5000);
        }

        // Anonymization (Enterprise Strategy). Hides classic sessions and authentication tokens!
        String safePayload = payloadToAnalyze;
        safePayload = safePayload.replaceAll("(?i)(_cfuvid|__cf_bm|sails\\.sid|jsessionid|phpsessid|aspsessionid|xsrf-token|csrf-token|auth)=[^;\\n\\r]+", "$1=[COOKIE]");
        safePayload = safePayload.replaceAll("mac=\"[^\"]+\"", "mac=\"[TOKEN]\"");
        safePayload = safePayload.replaceAll("(?i)boundary=[a-zA-Z0-9\\-_]+", "boundary=[BOUNDARY]");   safePayload = safePayload.replaceAll("(?i)Bearer [a-zA-Z0-9\\-_.]+", "Bearer [JWT]");
        safePayload = safePayload.replaceAll("(?i)(access_token|refresh_token|id_token|jwt)=[^;\\n\\r]+", "$1=[JWT]");
        safePayload = safePayload.replaceAll("Postman-Token: [a-zA-Z0-9\\-]+", "Postman-Token: [TOKEN]");

        try {
            boolean isMalicious = mlAnalyzer.isMalicious(safePayload);

            if (isMalicious) {
                logger.warn("AI WAF engine detected a malicious payload: \n{}", safePayload);
                throw new RequestRejectedException("Request rejected: AI WAF Engine detected a malicious payload.");
            }
        } catch (RequestRejectedException rre) {
            throw rre;
        } catch (Exception e) {
            logger.error("Error during AI WAF inspection: {}", e.getMessage(), e);
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
        HttpServletRequest underlyingRequest = request;
        if (request instanceof HttpServletRequestWrapper wrapper) {
            underlyingRequest = (HttpServletRequest) wrapper.getRequest();
        }

        if (underlyingRequest instanceof MultipartRequest originalMultipartRequest) {
            MultiValueMap<String, MultipartFile> newMultipartFiles = new LinkedMultiValueMap<>();

            // Track total bytes to prevent aggregate memory exhaustion (DOS)
            long totalBytesProcessed = 0;
            // You should define this limit globally alongside maxInMemoryFileSize
            long maxTotalMemorySize = maxInMemoryFileSize * 5;

            for (Map.Entry<String, List<MultipartFile>> entry : originalMultipartRequest.getMultiFileMap().entrySet()) {
                for (MultipartFile file : entry.getValue()) {
                    if (file.isEmpty()) continue;

                    // Clean Original Filename, safe to use in exception messages/logs
                    String safeOriginalFilename = getFilenameString(file);

                    // ANTI-DOS: Single file size limit
                    if (file.getSize() > maxInMemoryFileSize) {
                        throw new RequestRejectedException("Request rejected: File size exceeds WAF inspection limit for file '" + safeOriginalFilename + "'.");
                    }
                    // ANTI-DOS: Aggregate file size limit
                    totalBytesProcessed += file.getSize();
                    if (totalBytesProcessed > maxTotalMemorySize) {
                        throw new RequestRejectedException("Request rejected: Total upload size exceeds aggregate WAF memory limit.");
                    }

                    // Load file safely into memory
                    byte[] fileContent = file.getBytes();
                    String declaredContentType = file.getContentType();

                    // Magic Number Validation
                    int headerLength = Math.min(fileContent.length, 8);
                    String hexHeader = bytesToHex(Arrays.copyOfRange(fileContent, 0, headerLength));
                    String magicMimeType = magicNumbers.entrySet().stream()
                            .filter(e -> hexHeader.toUpperCase().startsWith(e.getKey()))
                            .map(Map.Entry::getValue)
                            .findFirst().orElse(null);

                    // ZIP check
                    boolean isZipBasedFormat = "application/zip".equals(magicMimeType) &&
                            declaredContentType != null &&
                            (declaredContentType.contains("officedocument") ||
                                    declaredContentType.equals("application/java-archive") ||
                                    declaredContentType.equals("application/epub+zip"));

                    if (magicMimeType != null && declaredContentType != null && !magicMimeType.equals(declaredContentType) && !isZipBasedFormat) {
                        throw new RequestRejectedException("Request rejected: Mime-type spoofing detected for file '" + safeOriginalFilename + "'.");
                    }

                    // XML/SVG Injection Validation
                    if (declaredContentType != null && xmlMimeTypes.contains(declaredContentType.toLowerCase())) {
                        String content = new String(fileContent, StandardCharsets.UTF_8);
                        // Check XXE in file content
                        if (!wafPredicate.xxePredicate.test(content)) {
                            throw new RequestRejectedException("Request rejected: XXE (External Entity) payload detected in XML file '" + safeOriginalFilename + "'.");
                        }
                        // Check XSS Active Scripting only in file content
                        if (!wafPredicate.fileXssPredicate.test(content)) {
                            throw new RequestRejectedException("Request rejected: Active XSS payload detected in XML/SVG file '" + safeOriginalFilename + "'.");
                        }
                    }

                    // Store with the sanitized filename
                    newMultipartFiles.add(entry.getKey(), new WAFTempMultipartFile(file.getName(), safeOriginalFilename, declaredContentType, fileContent));
                }
            }
            return new WAFMultipartRequestWrapper(request, newMultipartFiles);
        }
        return request;
    }

    /**
     * Get Filename as safe String
     * @param file MultipartFile file
     * @return String
     */
    private String getFilenameString(MultipartFile file) {
        String rawFilename = file.getOriginalFilename();
        return (rawFilename != null && !rawFilename.isEmpty())
                ? rawFilename.replaceAll("[^a-zA-Z0-9.]", "_")
                : "unknown_file";
    }

    /**
     * Handles STRICT mode. Rejects requests on pattern match.
     *
     * @param request The processed HttpServletRequest.
     */
    private void handleStrict(HttpServletRequest request) throws IOException, IllegalArgumentException {
        // Validate http headers (Critical for Log4Shell/SpEL/Deserialization)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!wafPredicate.getWAFHeaderNames().test(headerName)) {
                throw new RequestRejectedException("Request rejected: Disallowed pattern in Header name.");
            }
            String headerValue = request.getHeader(headerName);
            if (!wafPredicate.getWAFHeaderValues().test(headerValue)) {
                throw new RequestRejectedException("Request rejected: Disallowed pattern in Header value.");
            }
        }

        // Validate url / form parameters (XSS, SQLi in Query String)
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            if (!wafPredicate.getWAFParameterNames().test(entry.getKey())) {
                throw new RequestRejectedException("Request rejected: Disallowed pattern in Parameter name.");
            }
            for (String value : entry.getValue()) {
                if (!wafPredicate.getWAFParameterValues().test(value)) {
                    throw new RequestRejectedException("Request rejected: Disallowed pattern in Parameter value.");
                }
            }
        }

        // Validate cookies
        if (isDeepScanCookie) {
            validateCookies(request);
        }

        // Validate json/rest body
        String body = IOUtils.toString(request.getReader());
        if (!StringUtils.isBlank(body)) {
            if (!wafPredicate.getWAFRestApiBody().test(body)) {
                throw new RequestRejectedException("Request rejected: Disallowed WAF pattern found in Request Body.");
            }
        }
    }

    /**
     * Handles PASSIVE mode. Cleans the request on pattern match.
     *
     * @param request The processed HttpServletRequest.
     */
    private void handlePassive(HttpServletRequest request) throws IOException {
        // Warning: Sanitizing input by stripping patterns (Regex replaceAll) can lead to bypasses (e.g. <scr<script>ipt>).
        // It is recommended to use STRICT mode on APIs.
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
        for (byte b : bytes) { sb.append(String.format("%02X", b)); }
        return sb.toString();
    }

    /**
     * Host and User-Agent include AI.
     */
    private void validateHostAndUserAgent(HttpServletRequest request) {
        if (!wafPredicate.getWAFHostnames().test(request.getServerName())) {
            throw new RequestRejectedException("Request rejected: Disallowed Hostname.");
        }

        String userAgent = request.getHeader("User-Agent");
        if (wafPredicate.isUserAgentBlocked(userAgent)) {
            throw new RequestRejectedException("Request rejected: Disallowed User-Agent.");
        }
        if (wafPredicate.isAIUserAgentBlocked(userAgent)) {
            throw new RequestRejectedException("Request rejected: Disallowed AI User-Agent.");
        }
    }

    private void validateCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            if (cookies.length > 100) throw new RequestRejectedException("Request rejected: Exceeded cookie limit.");
            for (Cookie cookie : cookies) {
                if (!wafPredicate.getWAFParameterNames().test(cookie.getName())) {
                    throw new RequestRejectedException("Request rejected: Disallowed pattern in cookie name.");
                }
                if (!wafPredicate.getWAFParameterValues().test(cookie.getValue())) {
                    throw new RequestRejectedException("Request rejected: Disallowed pattern in cookie value.");
                }
            }
        }
    }

    private void handleRequestRejected(RequestRejectedException ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        logger.warn("WAF Blocked Request: {} RemoteAddr: {} RequestURL: {} {} UserAgent: {}",
                LogFormatUtils.formatValue(ex.getMessage(), !logger.isDebugEnabled()), // No truncated in debug mode!
                req.getRemoteAddr(), req.getMethod(), req.getServletPath(), req.getHeader("User-Agent"));

        resp.setStatus(HttpStatus.FORBIDDEN.value()); // FORBIDDEN (403) is standard for WAF drops, BAD_REQUEST (400) is okay too.
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

        byte[] responseBody = objectMapper.writeValueAsBytes(
                new Message("403", "SECURITY_VIOLATION", SOURCE, "Request rejected due to security policy violation.")
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
            //if (!wafPredicate.getWAFParameterNames().test(key)) continue; // skip this key and these values!
            String[] values = entry.getValue();
            String[] cleanedValues = new String[values.length];
            for (int i = 0; i < values.length; i++)
                cleanedValues[i] = stripWAFPatterns(values[i], wafPredicate.getXSSPatterns());
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
        if (value.length() > 100000) throw new RequestRejectedException("Input value is too long for passive sanitization.");
        String strippedValue = value;
        for (Pattern pattern : patterns) {
            strippedValue = pattern.matcher(strippedValue).replaceAll("");
        }
        return strippedValue;
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
        private Map<String, String[]> cachedParameterMap;

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
            // Return cached map if we've already built it
            if (this.cachedParameterMap != null) {
                return this.cachedParameterMap;
            }

            // Create a mutable copy of the original parameter map
            Map<String, String[]> mutableMap = new LinkedHashMap<>(super.getParameterMap());

            // Add your multipart file keys
            for (Map.Entry<String, List<MultipartFile>> entry : multipartFiles.entrySet()) {
                mutableMap.putIfAbsent(entry.getKey(), new String[0]);
            }

            // Lock the new map to respect the Servlet specification and cache it
            this.cachedParameterMap = Collections.unmodifiableMap(mutableMap);

            return this.cachedParameterMap;
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
        public Enumeration<String> getParameterNames() {
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
