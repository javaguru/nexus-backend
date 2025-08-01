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

package com.jservlet.nexus.controller;

import com.jservlet.nexus.config.web.WebConfig;
import com.jservlet.nexus.shared.service.backend.BackendService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Controller;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * The Status Controller
 */
@Controller
public class StatusController implements ResourceLoaderAware {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    private ResourceLoader resourceLoader;

    private String application;
    private String version;
    private String build;
    private String buildTime;
    private String revision;
    private String branch;
    private String profile;
    private String javaVmVersion;
    private String javaIoTmpdir;
    private String fileEncoding;

    private static final String sep = "\n";

    @Value("${serverName:#{null}}")
    private String serverName;

    @Value("${nexus.backend.uri.alive:#{null}}")
    private String uriAlive;

    private final BackendService backendService;
    private final Environment env;

    private static final String ENV_VAR = "environment";

    public StatusController(BackendService backendService, Environment env) {
        this.backendService = backendService;
        this.env = env;
    }

    @PostConstruct
    public void postConstruct() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:META-INF/version.properties");
        if (!resource.exists())
            throw new MissingResourceException("Unable to find \"version.properties\" on classpath!",
                    getClass().getName(), "version.properties");

        Properties properties = new Properties();
        properties.load(resource.getInputStream());
        application = properties.getProperty("application.name");
        version = properties.getProperty("version");
        build = properties.getProperty("build.number");
        buildTime = properties.getProperty("build.time");
        revision = properties.getProperty("build.revision");
        branch = properties.getProperty("build.branch");
        profile = properties.getProperty("build.profile");
        // only for Dev env.!
        javaVmVersion = System.getProperty("java.vm.version");
        javaIoTmpdir = System.getProperty("java.io.tmpdir");
        fileEncoding = Charset.defaultCharset().displayName();

        if (serverName == null) {
            serverName = System.getenv("COMPUTERNAME");
            if (serverName == null) {
                serverName = System.getenv("HOSTNAME");
                if (serverName == null) {
                    try { serverName = InetAddress.getLocalHost().getHostName(); }
                    catch (UnknownHostException ignored) {}
                }
            }
        }
    }

    @GetMapping(value = "/health/status", produces = MimeTypeUtils.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> status(HttpServletRequest request) { return createHealthStatusResponseEntity(request, true); }

    @GetMapping(value = "/health/controlpage", produces = MimeTypeUtils.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> controlpage(HttpServletRequest request) { return createHealthStatusResponseEntity(request, false); }

    private ResponseEntity<?> createHealthStatusResponseEntity(HttpServletRequest request, boolean withServices) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String now = sdf.format(System.currentTimeMillis());
        long currentTime = System.currentTimeMillis();
        long startup = WebConfig.getApplicationContext().getStartupDate();
        String started = sdf.format(startup);
        long days = getDaysBetween(currentTime, startup);

        final Health health = checkBackendServiceHealth() ? Health.OK : Health.ERROR;
        final Health lbHealth = health != Health.ERROR ? Health.OK : Health.ERROR;

        Map<String, String> status = new LinkedHashMap<>();
        status.put("Status timestamp", now);
        status.put("Application", application);
        status.put("Version", version);
        status.put("Startup date", started);
        status.put("Startup days", String.valueOf(days));
        status.put("Build profile", profile);
        status.put("Build number", build);
        status.put("Build timestamp", buildTime);
        status.put("Revision", revision);
        status.put("Branch", branch);
        status.put("Health", health.name());
        status.put("LB-Health", lbHealth.name());
        status.put("Server", serverName);
        // Dev only!
        if ("development".equals(env.getProperty(ENV_VAR))) {
            status.put("Jvm-version", javaVmVersion);
            status.put("Java io-tmpdir", javaIoTmpdir);
            status.put("Java fileEncoding", fileEncoding);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : status.entrySet()) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        if (withServices) {
            sb.append(sep).append(sep).append(sep);
            // Add services
            sb.append("Health-Services").append(sep);
            sb.append("--------------------------------------------------------------").append(sep);
            sb.append("Service-Name: ").append("Nexus-Backend Service").append(sep);
            sb.append("Service-Status: ").append(health).append(sep);
            sb.append("Backend-URL: ").append(backendService.getBackendURL()).append(sep);
            sb.append("Backend-Test-Uri: ").append(uriAlive).append(sep);
            sb.append("Removed-Header: ").append(backendService.isRemovedHeaders()).append(sep);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, new MimeType(MimeTypeUtils.TEXT_PLAIN, UTF_8).toString())
                .body(sb.toString());
    }

    private long getDaysBetween(long startTimeMillis, long endTimeMillis) {
        long diffInMillis = Math.abs(endTimeMillis - startTimeMillis);
        return TimeUnit.MILLISECONDS.toDays(diffInMillis);
    }

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private boolean checkBackendServiceHealth() {
        try {
           return backendService.get(uriAlive, backendService.createResponseType(Object.class)) != null;
        } catch (Exception e) {
            logger.error("BackendService uriAlive: '{}' Endpoint does not work properly: {}", uriAlive, e.getMessage());
            return false;
        }
    }

    private enum Health {
        OK(0),
        WARN(1),
        ERROR(2);

        private final int value;

        Health(int value) { this.value = value; }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return this.name();
        }
    }
}
