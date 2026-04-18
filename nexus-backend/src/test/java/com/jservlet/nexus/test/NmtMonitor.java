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

package com.jservlet.nexus.test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NEXUS WAF - NATIVE MEMORY TRACKING (Require the PID as an argument)
 */
public class NmtMonitor {

    // Regex to catch the Total and specific Categories
    private static final Pattern TOTAL_PATTERN = Pattern.compile("Total:\\s*reserved=(\\d+)KB,\\s*committed=(\\d+)KB");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("-\\s+([A-Za-z\\s]+?)\\s*\\(reserved=(\\d+)KB,\\s*committed=(\\d+)KB");

    public static void main(String[] args) {
        // Require the PID as an argument
        if (args.length < 1) {
            System.out.println("Error: Please specify the PID of the Java process.");
            System.out.println("Usage: java NmtMonitor <PID>");
            return;
        }

        String pid = args[0];
        System.out.println("Starting NMT monitoring for PID: " + pid + "...\n");

        try {
            // 1. Prepare the OS command
            ProcessBuilder processBuilder = new ProcessBuilder("jcmd", pid, "VM.native_memory", "summary");
            processBuilder.redirectErrorStream(true); // Merge stdout and stderr

            // 2. Execute the process
            Process process = processBuilder.start();

            // 3. Read the output stream
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean isNmtEnabled = false;

            System.out.println("===============================================================");
            System.out.println("     NEXUS WAF - NATIVE MEMORY TRACKING (PID: " + pid + ")");
            System.out.println("===============================================================");
            System.out.printf("%-25s | %-15s | %-15s%n", "Category", "Reserved (MB)", "Committed (MB)");
            System.out.println("--------------------------|-----------------|------------------");

            while ((line = reader.readLine()) != null) {
                // Check if NMT is enabled on the target process
                if (line.contains("Native Memory Tracking")) {
                    isNmtEnabled = true;
                }

                // Parse the Total
                Matcher totalMatcher = TOTAL_PATTERN.matcher(line);
                if (totalMatcher.find()) {
                    printFormattedRow("Total (Global)", totalMatcher.group(1), totalMatcher.group(2));
                    System.out.println("--------------------------|-----------------|------------------");
                    continue;
                }

                // Parse the subcategories
                Matcher categoryMatcher = CATEGORY_PATTERN.matcher(line);
                if (categoryMatcher.find()) {
                    String categoryName = categoryMatcher.group(1).trim();

                    // Rename "Internal" to clarify its AI/ONNX usage for your context
                    if (categoryName.equals("Internal")) {
                        categoryName = "Internal (ONNX AI)";
                    }

                    printFormattedRow(categoryName, categoryMatcher.group(2), categoryMatcher.group(3));
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || !isNmtEnabled) {
                System.out.println("\n Warning: The process returned an error or NMT is not enabled.");
                System.out.println("Did you start your application with -XX:NativeMemoryTracking=summary ?");
            } else {
                System.out.println("===============================================================\n");
            }

        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
        }
    }

    /**
     * Converts KB to MB and prints a formatted row for the table
     */
    private static void printFormattedRow(String category, String reservedKbStr, String committedKbStr) {
        double reservedMb = Long.parseLong(reservedKbStr) / 1024.0;
        double committedMb = Long.parseLong(committedKbStr) / 1024.0;

        System.out.printf("%-25s | %-12.2f MB | %-12.2f MB%n", category, reservedMb, committedMb);
    }
}
