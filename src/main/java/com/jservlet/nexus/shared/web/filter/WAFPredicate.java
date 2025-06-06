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

package com.jservlet.nexus.shared.web.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * WAF Predicate for HeaderNames, HeaderValues, ParameterNames, ParameterValues and Hostnames:
 * Check for potential evasion the length Parameter Names/Values, length Header Names/Values and length Hostnames
 * Define Patterns XSS, SQL, Google, Command, File and Link injections.
 */
public class WAFPredicate {

    final private XSSPattern x = new XSSPattern();
    final private SQLPattern s = new SQLPattern();
    final private GOOGLEPattern g = new GOOGLEPattern();
    final private CMDPattern c = new CMDPattern();
    final private FILEPattern f = new FILEPattern();
    final private LINKPattern l = new LINKPattern();

    private int parameterNamesLength = 255;
    private int parameterValuesLength = 1000000;
    private int headerNamesLength = 255;
    private int headerNamesValuesLength = 25000;
    private int hostNamesLength = 255;

    /**
     * Constructor
     * @param parameterNamesLength      Names parameter Length
     * @param parameterValuesLength     Values parameter Length
     * @param headerNamesLength         Header names Length
     * @param headerNamesValuesLength   Header names values Length
     * @param hostNamesLength           Host names Length
     */
    public WAFPredicate(int parameterNamesLength, int parameterValuesLength, int headerNamesLength,
                        int headerNamesValuesLength, int hostNamesLength) {
        this.parameterNamesLength = parameterNamesLength;
        this.parameterValuesLength = parameterValuesLength;
        this.headerNamesLength = headerNamesLength;
        this.headerNamesValuesLength = headerNamesValuesLength;
        this.hostNamesLength = hostNamesLength;
    }

    /**
     * Constructor
     */
    public WAFPredicate() {
    }

    final private Predicate<String> WAFParameterNames = (param) -> {
        if (param.length() > parameterNamesLength) return true;
        return x.test(param) && s.test(param) && c.test(param) && f.test(param) && l.test(param);
    };
    final private Predicate<String> WAFParameterValues = (param) -> {
        if (param.length() > parameterValuesLength) return true;
        return x.test(param) && s.test(param) && c.test(param) && f.test(param) && l.test(param);
    };
    final private Predicate<String> WAFHeaderNames = (header) -> {
        if (header.length() > headerNamesLength) return true;
        return x.test(header);
    };
    final private Predicate<String> WAFHeaderValues = (header) -> {
        if (header.length() > headerNamesValuesLength) return true;
        return x.test(header);
    };
    final private Predicate<String> WAFHostnames = (header) -> {
        if (header.length() > hostNamesLength) return true;
        return x.test(header);
    };

    public static class XSSPattern implements Predicate<String> {
        @Override
        public boolean test(String v) {
            return !WAFUtils.isWAFPattern(v, WAFUtils.xssPattern);
        }

        public List<Pattern> getPatterns() {
            return WAFUtils.xssPattern;
        }
    }

    public static class SQLPattern implements Predicate<String> {
        @Override
        public boolean test(String v) {
            return !WAFUtils.isWAFPattern(v, WAFUtils.sqlPattern);
        }

        public List<Pattern> getPatterns() {
            return WAFUtils.sqlPattern;
        }
    }

    public static class GOOGLEPattern implements Predicate<String> {
        @Override
        public boolean test(String v) {
            return !WAFUtils.isWAFPattern(v, WAFUtils.googlePattern);
        }

        public List<Pattern> getPatterns() {
            return WAFUtils.googlePattern;
        }
    }

    public static class CMDPattern implements Predicate<String> {
        @Override
        public boolean test(String v) {
            return !WAFUtils.isWAFPattern(v, WAFUtils.commandPattern);
        }

        public List<Pattern> getPatterns() {
            return WAFUtils.commandPattern;
        }
    }

    public static class FILEPattern implements Predicate<String> {
        @Override
        public boolean test(String v) {
            return !WAFUtils.isWAFPattern(v, WAFUtils.filePattern);
        }

        public List<Pattern> getPatterns() {
            return WAFUtils.filePattern;
        }
    }

    public static class LINKPattern implements Predicate<String> {
        @Override
        public boolean test(String v) {
            return !WAFUtils.isWAFPattern(v, WAFUtils.linkPattern);
        }

        public List<Pattern> getPatterns() {
            return WAFUtils.linkPattern;
        }
    }

    /**
     * Get the Waf list of Pattern to be tested
     * @return List  A List Pattern
     */
    public List<Pattern> getWafPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        patterns.addAll(x.getPatterns());
        patterns.addAll(g.getPatterns());
        patterns.addAll(s.getPatterns());
        patterns.addAll(c.getPatterns());
        patterns.addAll(f.getPatterns());
        patterns.addAll(l.getPatterns());
        return patterns;
    }

    public List<Pattern> getXSSPatterns() {
        return new ArrayList<>(x.getPatterns());
    }

    public Predicate<String> getWAFParameterNames() {
        return WAFParameterNames;
    }

    public Predicate<String> getWAFParameterValues() {
        return WAFParameterValues;
    }

    public Predicate<String> getWAFHeaderNames() {
        return WAFHeaderNames;
    }

    public Predicate<String> getWAFHeaderValues() {
        return WAFHeaderValues;
    }

    public Predicate<String> getWAFHostnames() {
        return WAFHostnames;
    }

}
