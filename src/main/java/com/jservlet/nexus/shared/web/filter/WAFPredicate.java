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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * WAF Predicate check for potential evasion.
 * <p>
 * The "Defense in Depth" Principle, Protection Against Poor Code, Zero Trust!<br>
 * Checks for HeaderNames, HeaderValues, ParameterNames, ParameterValues and Hostnames, UserAgents<br>
 * Define Patterns XSS, SQL, Google, Command, File and Link injections.<br>
 * Define User-Agent filter and Suspicious Characters filter.<br>
 */
public class WAFPredicate {

    private static final Logger logger = LoggerFactory.getLogger(WAFPredicate.class);

    // Predicate instances for different attack patterns, initialized with patterns from WAFUtils
    private final WafPatternPredicate xssPredicate = new WafPatternPredicate(WAFUtils.xssPattern);
    private final WafPatternPredicate sqlPredicate = new WafPatternPredicate(WAFUtils.sqlPattern);
    private final WafPatternPredicate googlePredicate = new WafPatternPredicate(WAFUtils.googlePattern);
    private final WafPatternPredicate cmdPredicate = new WafPatternPredicate(WAFUtils.commandPattern);
    private final WafPatternPredicate filePredicate = new WafPatternPredicate(WAFUtils.filePattern);
    private final WafPatternPredicate linkPredicate = new WafPatternPredicate(WAFUtils.linkPattern);
    private final WafPatternPredicate userAgentPredicate = new WafPatternPredicate(WAFUtils.userAgentPattern);
    private final WafPatternPredicate suspiciousPredicate = new WafPatternPredicate(WAFUtils.suspiciousPattern);

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

    /**
     * A reusable predicate that tests a string against a list of compiled regex patterns.
     * This predicate returns {@code true} if the input string is considered "safe" (i.e.,
     * it does NOT match any of the configured malicious patterns).
     */
    public static class WafPatternPredicate implements Predicate<String> {
        private final List<Pattern> patterns;

        /**
         * Constructs a predicate with a specific list of patterns.
         *
         * @param patterns The list of {@link Pattern} to test against.
         */
        public WafPatternPredicate(List<Pattern> patterns) {
            this.patterns = Objects.requireNonNull(patterns, "Pattern list cannot be null.");
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
            if (value == null) {
                return true; // Null values are considered safe
            }
            return !WAFUtils.isWAFPattern(value, patterns);
        }

        /**
         * Gets an unmodifiable view of the patterns used by this predicate.
         * @return An unmodifiable list of {@link Pattern}.
         */
        public List<Pattern> getPatterns() {
            return Collections.unmodifiableList(patterns);
        }
    }


    /**
     * Predicate for validating HTTP parameter names.
     * Checks for length, XSS, SQLi, Command, File, and Link injection patterns.
     */
    public final Predicate<String> forParameterNames = (param) -> {
        if (param == null) return true; // Null is safe
        if (param.length() > parameterNamesLength) {
            logger.warn("Blocked: Parameter name exceeds max length ({}): {}", parameterNamesLength, param);
            return false;
        }
        return xssPredicate.test(param) &&
                sqlPredicate.test(param) &&
                cmdPredicate.test(param) &&
                filePredicate.test(param) &&
                linkPredicate.test(param);
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
                cmdPredicate.test(value) &&
                filePredicate.test(value) &&
                linkPredicate.test(value);
    };

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
        return xssPredicate.test(header);
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
        return xssPredicate.test(header);
    };

    /**
     * Predicate for validating hostnames.
     * Checks for length and allowed hostnames, Filter Suspicious and XSS.
     */
    public final Predicate<String> forHostnames = (hostname) -> {
        if (hostname == null) return true; // Null is safe
        if (hostname.length() > hostNamesLength) {
            logger.warn("Blocked: Hostname exceeds max length ({}): {}", hostNamesLength, hostname);
            return false;
        }
        // Check against allowed hostnames if a pattern is configured
        if (!"".equals(allowedHostnames.pattern()) && !allowedHostnames.matcher(hostname).matches()) {
            logger.warn("Blocked: Hostname {} is not in the allowed list.", hostname);
            return false;
        }
        // Check suspicious and XSS even on allowed hostnames.
        return suspiciousPredicate.test(hostname) && xssPredicate.test(hostname);
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

    public List<Pattern> getXSSPatterns() { return xssPredicate.getPatterns(); }

    public Predicate<String> getWAFParameterNames() { return forParameterNames; }
    public Predicate<String> getWAFParameterValues() { return forParameterValues; }
    public Predicate<String> getWAFHeaderNames() { return forHeaderNames; }
    public Predicate<String> getWAFHeaderValues() { return forHeaderValues; }
    public Predicate<String> getWAFHostnames() { return forHostnames; }


    /* Getters and Setters */

    public boolean isBlockDisallowedUserAgents() {
        return blockDisallowedUserAgents;
    }

    public void setBlockDisallowedUserAgents(boolean blockDisallowedUserAgents) {
        this.blockDisallowedUserAgents = blockDisallowedUserAgents;
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
}
