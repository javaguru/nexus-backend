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

package com.jservlet.nexus.shared.service.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NmtMonitorService - Native Memory Tracking
 * <p>
 * - Native: System calls (malloc/mmap) in C++, ONNX model.<br>
 * - Memory: Tracks the actual RAM footprint.<br>
 * - Tracking: The -XX:NativeMemoryTracking=summary option enables internal instrumentation, allowing jcmd to generate reports.
 */
public class NmtMonitorService {

    private static final Logger log = LoggerFactory.getLogger(NmtMonitorService.class);
    private static final String TARGET_APP_EMBEDDED = "com.jservlet.nexus.config.Application";
    private static final String TARGET_APP_WAR = "org.apache.catalina.startup.Bootstrap";

    // Regex patterns for parsing
    private static final Pattern TOTAL_PATTERN = Pattern.compile("Total:\\s*reserved=(\\d+)KB,\\s*committed=(\\d+)KB");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("-\\s+([A-Za-z\\s]+?)\\s*\\(reserved=(\\d+)KB,\\s*committed=(\\d+)KB");

    /**
     * Finds the PID of the application using 'jps -l'
     */
    public Optional<String> findApplicationPid() {
        try {
            Process process = new ProcessBuilder("jps", "-l").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(TARGET_APP_EMBEDDED) || line.contains(TARGET_APP_WAR)) {
                        return Optional.of(line.split("\\s+")[0]);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error while running jps -l", e);
        }
        return Optional.empty();
    }

    /**
     * Executes jcmd and returns a human-readable report
     */
    public String getNativeMemoryReport() {
        Optional<String> pidOpt = findApplicationPid();
        if (pidOpt.isEmpty()) {
            return "Error: Could not find PID for " + TARGET_APP_EMBEDDED + " OR " + TARGET_APP_WAR;
        }

        String pid = pidOpt.get();
        StringBuilder report = new StringBuilder();

        try {
            Process process = new ProcessBuilder("jcmd", pid, "VM.native_memory", "summary").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                report.append("===============================================================\n");
                report.append("     NEXUS WAF - NMT REPORT (PID: ").append(pid).append(")\n");
                report.append("===============================================================\n");
                report.append(String.format("%-25s | %-15s | %-15s%n", "Category", "Reserved (MB)", "Committed (MB)"));
                report.append("--------------------------|-----------------|------------------\n");

                String line;
                while ((line = reader.readLine()) != null) {
                    // Total Parsing
                    Matcher totalMatcher = TOTAL_PATTERN.matcher(line);
                    if (totalMatcher.find()) {
                        report.append(formatRow("Total (Global)", totalMatcher.group(1), totalMatcher.group(2)));
                        report.append("--------------------------|-----------------|------------------\n");
                        continue;
                    }

                    // Category Parsing
                    Matcher catMatcher = CATEGORY_PATTERN.matcher(line);
                    if (catMatcher.find()) {
                        String name = catMatcher.group(1).trim();
                        if (name.equals("Internal")) name = "Internal (ONNX AI)";
                        report.append(formatRow(name, catMatcher.group(2), catMatcher.group(3)));
                    }
                }
                report.append("===============================================================\n");
            }
        } catch (Exception e) {
            log.error("Error while running jcmd", e);
            return "Error executing jcmd: " + e.getMessage();
        }

        return report.toString();
    }

    private String formatRow(String category, String reservedKb, String committedKb) {
        double resMb = Long.parseLong(reservedKb) / 1024.0;
        double comMb = Long.parseLong(committedKb) / 1024.0;
        return String.format("%-25s | %-12.2f MB | %-12.2f MB%n", category, resMb, comMb);
    }
}
