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

package com.jservlet.nexus.config.web.tomcat;

import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.core.StandardThreadExecutor;
import org.apache.catalina.realm.LockOutRealm;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.catalina.valves.HealthCheckValve;
import org.apache.tomcat.util.descriptor.web.LoginConfig;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Custom Container with an Embedded Tomcat: <br>
 * - Customizes HTTP Connector <br>
 * - Access Log Valve <br>
 * - Error Report Valve <br>
 * - ACL / Security constraint configuration <br>
 *  <br>
 *  SpringBoot managed the Server, Catalina Service, Engine and the localhost Host by the WebServerFactoryCustomizer
 */
@Component
@Profile("withTomcat")
public class TomcatCustomContainer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final static Logger logger = LoggerFactory.getLogger(TomcatCustomContainer.class);

    @Value("${nexus.backend.tomcat.accesslog.valve.enable:false}")
    private boolean accessLogEnabled;

    @Value("${nexus.backend.tomcat.healthcheck.enable:true}")
    private boolean healthCheckEnabled;

    @Value("${nexus.backend.tomcat.healthcheck.path:/health}")
    private String healthCheckPath;

    // Accesslog properties
    @Value("${nexus.backend.tomcat.accesslog.directory:}")
    private String directory;
    @Value("${nexus.backend.tomcat.accesslog.suffix:.log}")
    private String suffix;
    @Value("${nexus.backend.tomcat.accesslog.prefix:nexus_backend_access_log}")
    private String prefix;
    @Value("${nexus.backend.tomcat.accesslog.pattern:%t %I %a %S %m %U %s %b %{Content-Length}i %T %u %{Authorization}i %{User-Agent}i %{X-Forwarded-For}i}")
    private String pattern;
    @Value("${nexus.backend.tomcat.accesslog.encoding:UTF-8}")
    private String encoding;
    @Value("${nexus.backend.tomcat.accesslog.checkExists:false}")
    private boolean checkExists;
    @Value("${nexus.backend.tomcat.accesslog.asyncSupported:true}")
    private boolean asyncSupported;
    @Value("${nexus.backend.tomcat.accesslog.renameOnRotate:true}")
    private boolean renameOnRotate;
    @Value("${nexus.backend.tomcat.accesslog.maxDays:-1}")
    private int maxDays;

    // Error Report properties
    @Value("${nexus.backend.tomcat.error-report.showReport:false}")
    private boolean showReport;
    @Value("${nexus.backend.tomcat.error-report.showServerInfo:false}")
    private boolean showServerInfo;

    // Security constraints properties
    @Value("${nexus.backend.tomcat.security.patterns:/actuator/*,/nmt/*}")
    private String[] securityPatterns;
    @Value("${nexus.backend.tomcat.security.health.patterns:/health/*}")
    private String[] healthPatterns;
    @Value("${nexus.backend.tomcat.security.admin.acl.enable:true}")
    private boolean adminAclEnabled;
    @Value("${nexus.backend.tomcat.security.users.file:}")
    private String customUsersFilePath;
    @Value("#{'${nexus.backend.tomcat.security.gui.roles:admin-gui}'.split(',')}")
    private List<String> securitiesRoles;
    @Value("#{'${nexus.backend.tomcat.security.health.roles:admin-health,admin-gui}'.split(',')}")
    private List<String> healthRoles;

    // Connector properties (HTTP)
    @Value("${nexus.backend.tomcat.connector.accept-count:100}")
    private int acceptCount;
    @Value("${nexus.backend.tomcat.connector.connection-timeout:20000}")
    private int connectionTimeout;
    @Value("${nexus.backend.tomcat.connector.max-post-size:10485760}")
    private int maxPostSize;
    @Value("${nexus.backend.tomcat.connector.disable-upload-timeout:true}")
    private boolean disableUploadTimeout;
    @Value("${nexus.backend.tomcat.connector.compression:on}")
    private String compression;
    @Value("${nexus.backend.tomcat.connector.compressable-mime-type:text/html,text/xml,text/plain,text/javascript,text/css,application/json}")
    private String compressableMimeType;
    @Value("${nexus.backend.tomcat.connector.uri-encoding:UTF-8}")
    private String uriEncoding;
    @Value("${nexus.backend.tomcat.connector.max-http-header-size:10240}")
    private int maxHttpHeaderSize;
    @Value("${nexus.backend.tomcat.connector.reject-illegal-header:true}")
    private boolean rejectIllegalHeader;
    @Value("${nexus.backend.tomcat.connector.server:git di nexus a}")
    private String serverHeader;

    @Value("${nexus.backend.tomcat.executor.maxThreads:300}")
    private int maxThreads;
    @Value("${nexus.backend.tomcat.executor.minSpareThreads:4}")
    private int minSpareThreads;

    @Value("${nexus.backend.tomcat.acl.realm.lock.failureCount:5}")  // Max 5 attempts
    private int failureCount;
    @Value("${nexus.backend.tomcat.acl.realm.lock.lockOutTime:300}") // 5 minutes lockout
    private int lockOutTime;


    @Override
    public void customize(TomcatServletWebServerFactory factory) {

        // Configure HTTP Connector and Valves
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("acceptCount", String.valueOf(acceptCount));
            connector.setProperty("connectionTimeout", String.valueOf(connectionTimeout));
            connector.setProperty("maxPostSize", String.valueOf(maxPostSize));
            connector.setProperty("disableUploadTimeout", String.valueOf(disableUploadTimeout));

            // Compression, encoding
            connector.setProperty("compression", compression);
            connector.setProperty("compressableMimeType", compressableMimeType);
            connector.setURIEncoding(uriEncoding);

            // Security and Headers
            connector.setProperty("maxHttpHeaderSize", String.valueOf(maxHttpHeaderSize));
            connector.setProperty("rejectIllegalHeader", String.valueOf(rejectIllegalHeader));
            connector.setProperty("server", serverHeader);

            logger.info("Configured Default HTTP Connector: acceptCount={}, connectionTimeout={}, maxPostSize={}, disableUploadTimeout={}," +
                            "compression={},  compressableMimeType={}, uriEncoding={}," +
                            "maxHttpHeaderSize={}, rejectIllegalHeader={}, serverHeader={}",
                    acceptCount, connectionTimeout, maxPostSize, disableUploadTimeout,
                    compression, compressableMimeType, uriEncoding,
                    maxHttpHeaderSize, rejectIllegalHeader, serverHeader
            );

            // Creating the thread pool
            StandardThreadExecutor executor = new StandardThreadExecutor();
            executor.setName("tomcatThreadPool");
            executor.setNamePrefix("catalina-exec-");
            executor.setMaxThreads(maxThreads);
            executor.setMinSpareThreads(minSpareThreads);

            // Attachment to the Default HTTP Service and Connector
            connector.getService().addExecutor(executor);
            connector.getProtocolHandler().setExecutor(executor);

            logger.info("Global Executor 'tomcatThreadPool' created and attached to default HTTP Connector." +
                    "maxThreads: {}, minSpareThreads: {}", maxThreads, minSpareThreads);
        });

        // Health Check Valve
        if (healthCheckEnabled) {
            HealthCheckValve healthCheckValve = new HealthCheckValve();
            healthCheckValve.setPath(healthCheckPath);
            factory.addEngineValves(healthCheckValve);
            logger.info("Starting Tomcat Catalina HealthCheck Valve on path: {}", healthCheckPath);
        }

        // Access Log Valve
        if (accessLogEnabled) {
            logger.info("Starting Tomcat Catalina AccessLog Valve");
            factory.addEngineValves(getAccessLogValve());
        }

        // Error Report Valve (Hides server version and detailed reports)
        ErrorReportValve errorReportValve = new ErrorReportValve();
        errorReportValve.setShowReport(showReport);
        errorReportValve.setShowServerInfo(showServerInfo);
        factory.addEngineValves(errorReportValve);
        logger.info("Starting Tomcat Catalina ErrorReport Valve: showReport={}, showServerInfo={}", showReport, showServerInfo);


        // Unified security configuration (Realm, LockOut, and Constraints)
        if (adminAclEnabled) {
            factory.addContextCustomizers(context -> {
                MemoryRealm memoryRealm = new MemoryRealm();
                File userConfigFile = null;

                try {
                    // Check specific path
                    if (customUsersFilePath != null && !customUsersFilePath.isEmpty()) {
                        userConfigFile = new File(customUsersFilePath);
                    }

                    // Auto-detection: catalina.base
                    if ((userConfigFile == null || !userConfigFile.exists()) && System.getProperty("catalina.base") != null) {
                        File candidate = new File(System.getProperty("catalina.base"), "conf/tomcat-users.xml");
                        if (candidate.exists()) userConfigFile = candidate;
                    }

                    // Auto-detection: catalina.home
                    if ((userConfigFile == null || !userConfigFile.exists()) && System.getProperty("catalina.home") != null) {
                        File candidate = new File(System.getProperty("catalina.home"), "conf/tomcat-users.xml");
                        if (candidate.exists()) userConfigFile = candidate;
                    }

                    // Fallback to embedded classpath resource
                    if (userConfigFile != null && userConfigFile.exists()) {
                        logger.info("Loading tomcat-users.xml from path: {}", userConfigFile.getAbsolutePath());
                        memoryRealm.setPathname(userConfigFile.getAbsolutePath());
                    } else {
                        ClassPathResource resource = new ClassPathResource("tomcat-users.xml");
                        if (resource.exists()) {
                            userConfigFile = File.createTempFile("users-embedded-", ".xml");
                            userConfigFile.deleteOnExit();

                            try (InputStream inputStream = resource.getInputStream()) {
                                Files.copy(inputStream, userConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            memoryRealm.setPathname(userConfigFile.getAbsolutePath());
                            logger.info("Embedded tomcat-users.xml extracted to: {}", userConfigFile.getAbsolutePath());
                        } else {
                            logger.error("tomcat-users.xml not found in classpath!");
                        }
                    }
                } catch (IOException e) {
                    logger.error("Failed to manage tomcat-users.xml configuration", e);
                }

                // Security Realm configuration (Always Active)
                logger.info("Initializing Tomcat Security Realm with LockOut Protection");

                // Wrap in LockOutRealm to prevent brute force attacks
                LockOutRealm lockOutRealm = new LockOutRealm();
                lockOutRealm.addRealm(memoryRealm);
                lockOutRealm.setFailureCount(failureCount);
                lockOutRealm.setLockOutTime(lockOutTime);

                // Assign the realm to the context
                context.setRealm(lockOutRealm);

                // Programmatic constraints & authenticator (always applied)
                // Spring Boot Embedded completely ignores web.xml. We MUST define ACLs programmatically.
                logger.info("Applying programmatic security constraints and authenticator");

                // Admin Area Constraints (/actuator, /nmt)
                SecurityConstraint adminConstraint = new SecurityConstraint();
                adminConstraint.setUserConstraint("NONE");
                adminConstraint.setDisplayName("Nexus Admin Access Constraint");
                adminConstraint.setAuthConstraint(true);

                SecurityCollection adminCol = new SecurityCollection();
                for (String p : securityPatterns) {
                    adminCol.addPattern(p.trim());
                    logger.info("- Protected Admin path: {}", p.trim());
                }
                adminConstraint.addCollection(adminCol);

                for (String role : securitiesRoles) {
                    adminConstraint.addAuthRole(role);
                    context.addSecurityRole(role);
                    logger.info("- Authorized Admin role: {}", role);
                }
                context.addConstraint(adminConstraint);

                // Health Area Constraints (/health)
                SecurityConstraint healthConstraint = new SecurityConstraint();
                healthConstraint.setUserConstraint("NONE");
                healthConstraint.setDisplayName("Nexus Health Access Constraint");
                healthConstraint.setAuthConstraint(true);

                SecurityCollection healthCol = new SecurityCollection();
                for (String p : healthPatterns) {
                    healthCol.addPattern(p.trim());
                    logger.info("- Protected Health path: {}", p.trim());
                }
                healthConstraint.addCollection(healthCol);

                for (String role : healthRoles) {
                    healthConstraint.addAuthRole(role);
                    context.addSecurityRole(role);
                    logger.info("- Authorized Health role: {}", role);
                }
                context.addConstraint(healthConstraint);

                // Authentication Method (Programmatic fallback)
                LoginConfig loginConfig = new LoginConfig();
                loginConfig.setAuthMethod("BASIC");
                loginConfig.setRealmName("Nexus Backend Realm");
                context.setLoginConfig(loginConfig);

                // Inject the Basic Authenticator Valve
                context.getPipeline().addValve(new BasicAuthenticator());

                logger.info("Programmatic Security configuration completed.");
            });
        }
    }

    /**
     * Helper to configure the AccessLog Valve
     */
    private AccessLogValve getAccessLogValve() {
        AccessLogValve valve = new AccessLogValve();
        valve.setDirectory(directory);
        valve.setPrefix(prefix);
        valve.setSuffix(suffix);
        valve.setPattern(pattern);
        valve.setEncoding(encoding);
        valve.setAsyncSupported(asyncSupported);
        valve.setRenameOnRotate(renameOnRotate);
        valve.setCheckExists(checkExists);
        valve.setMaxDays(maxDays);
        return valve;
    }
}
