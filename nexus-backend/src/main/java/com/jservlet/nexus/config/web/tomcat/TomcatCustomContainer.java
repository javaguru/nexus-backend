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

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.BasicAuthenticator;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
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
 * - Customizes Connector Http <br>
 * - Extended Access Log Valve <br>
 * - Error ReportValve <br>
 * - Configuration ACL / Security constraint <br>
 */
@Component
@Profile("withTomcat") // Embedded!
public class TomcatCustomContainer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final static Logger logger = LoggerFactory.getLogger(TomcatCustomContainer.class);

    @Value("${nexus.backend.tomcat.embedded.webxml.file:}")
    private String externalWebXmlPath;

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
    @Value("${nexus.backend.tomcat.accesslog.checkExists:true}")
    private boolean checkExists;
    @Value("${nexus.backend.tomcat.accesslog.asyncSupported:true}")
    private boolean asyncSupported;
    @Value("${nexus.backend.tomcat.accesslog.renameOnRotate:true}")
    private boolean renameOnRotate;
    @Value("${nexus.backend.tomcat.accesslog.maxDays:-1}")
    private int maxDays;

    // ErrorReport properties
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


    @Override
    public void customize(TomcatServletWebServerFactory factory) {

        // Manage web.xml file (External ou Embedded)
        factory.addContextCustomizers(new TomcatContextCustomizer() {
            @Override
            public void customize(Context context) {
                try {
                    File webXmlFile = null;

                    // Priority external file check
                    if (externalWebXmlPath != null && !externalWebXmlPath.isEmpty()) {
                        File extFile = new File(externalWebXmlPath);
                        if (extFile.exists()) {
                            webXmlFile = extFile;
                        } else {
                            logger.warn("The external web.xml file was not found : {}", extFile.getAbsolutePath());
                        }
                    }

                    // Fallback on web.xml embedded in the classpath
                    if (webXmlFile == null) {
                        String[] candidatePaths = {
                            "META-INF/resources/WEB-INF/web.xml", // standard Spring Boot for a webapp
                            "WEB-INF/web.xml",
                            "web.xml"                             // Fallback include root
                        };

                        for (String path : candidatePaths) {
                            ClassPathResource resource = new ClassPathResource(path);
                            if (resource.exists()) {
                                logger.info("Found embedded web.xml at: {}", path);
                                webXmlFile = File.createTempFile("web-embedded-", ".xml");
                                webXmlFile.deleteOnExit();
                                try (InputStream inputStream = resource.getInputStream()) {
                                    Files.copy(inputStream, webXmlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                }
                                // Default HTTP Connector Configuration
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
                                });
                                break;
                            }
                        }
                    }

                    // Tomcat context assignment
                    if (webXmlFile != null && webXmlFile.exists()) {
                        logger.info("Force Embedded Tomcat to use web.xml: {}", webXmlFile.getAbsolutePath());
                        context.setAltDDName(webXmlFile.getAbsolutePath());
                    } else {
                        logger.warn("No web.xml found in common locations. Relying on Spring Boot programmatic constraints.");
                    }

                } catch (IOException e) {
                    logger.error("Error during web.xml extraction", e);
                }
            }
        });

        // Valves (HealthCheck, AccessLog, ErrorReport)
        if (healthCheckEnabled) {
            HealthCheckValve healthCheckValve = new HealthCheckValve();
            healthCheckValve.setPath(healthCheckPath);
            factory.addEngineValves(healthCheckValve);
            logger.info("Starting Tomcat Catalina HealthCheck Valve on path: {}", healthCheckPath);
        }

        // Access logs
        if (accessLogEnabled) {
            logger.info("Starting Tomcat Catalina AccessLog Valve");
            factory.addEngineValves(getAccessLogValve());
        } else {
            logger.debug("Tomcat AccessLog Valve is disabled");
        }

        // Error Report, no server version and no report
        ErrorReportValve errorReportValve = new ErrorReportValve();
        errorReportValve.setShowReport(showReport);
        errorReportValve.setShowServerInfo(showServerInfo);
        factory.addEngineValves(errorReportValve); // GLOBAL!
        logger.info("Starting Tomcat Catalina ErrorReport Valve: showReport {} - ShowServerInfo {}", showReport, showServerInfo);


        // Configuration ACL / Security constraint web.xml and tomcat-users.xml
        factory.addContextCustomizers(context -> {
            MemoryRealm memoryRealm = new MemoryRealm();
            File userConfigFile = null;

            try {
                // specific path
                if (customUsersFilePath != null && !customUsersFilePath.isEmpty()) {
                    userConfigFile = new File(customUsersFilePath);
                } else {
                    logger.warn("The external tomcat-users.xml is not configured");
                }

                // auto-detect catalina.base
                if ((userConfigFile == null || !userConfigFile.exists()) && System.getProperty("catalina.base") != null) {
                    File candidate = new File(System.getProperty("catalina.base"), "conf/tomcat-users.xml");
                    if (candidate.exists()) userConfigFile = candidate;
                }

                // auto-detect catalina.home
                if ((userConfigFile == null || !userConfigFile.exists()) && System.getProperty("catalina.home") != null) {
                    File candidate = new File(System.getProperty("catalina.home"), "conf/tomcat-users.xml");
                    if (candidate.exists()) userConfigFile = candidate;
                }

                // Application or Fallback Classpath (Embedded)
                if (userConfigFile != null && userConfigFile.exists()) {
                    logger.info("Loading tomcat-users.xml from path {}", userConfigFile.getAbsolutePath());
                    memoryRealm.setPathname(userConfigFile.getAbsolutePath());
                } else {
                    // File embedded src/main/resources
                    logger.info("Loading embedded tomcat-users.xml from classpath");
                    ClassPathResource resource = new ClassPathResource("tomcat-users.xml");

                    if (resource.exists()) {
                        userConfigFile = File.createTempFile("tomcat-users-embedded-", ".xml");
                        userConfigFile.deleteOnExit();

                        try (InputStream inputStream = resource.getInputStream()) {
                            Files.copy(inputStream, userConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                        memoryRealm.setPathname(userConfigFile.getAbsolutePath());
                        logger.info("Embedded tomcat-users.xml extracted to: {}", userConfigFile.getAbsolutePath());
                    } else {
                        logger.error("File tomcat-users.xml not found in classpath!");
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load or extract tomcat-users.xml configuration", e);
            }

            // Programmatic Constraints (Only if no web.xml is found)
            if (context.getAltDDName() == null) {
                logger.info("Configuring Tomcat Security Constraint and Realm");

                // Encapsulate in a LockOutRealm (Protection Brute Force)
                LockOutRealm lockOutRealm = new LockOutRealm();
                lockOutRealm.addRealm(memoryRealm);
                lockOutRealm.setFailureCount(5); // 5 failed max
                lockOutRealm.setLockOutTime(300); // block 300s

                // Assign LockOutRealm
                context.setRealm(lockOutRealm);

                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("NONE");
                securityConstraint.setDisplayName("Nexus Admin Access Constraint");
                securityConstraint.setAuthConstraint(true);
                logger.info("Create Security Constraint : {}", securityConstraint.getDisplayName());

                SecurityCollection collection = new SecurityCollection();
                for (String p : securityPatterns) {
                    collection.addPattern(p.trim());
                    logger.info("- Path Security : {}", p.trim());
                }
                securityConstraint.addCollection(collection);

                for (String securityRole : securitiesRoles) {
                    securityConstraint.addAuthRole(securityRole);
                    context.addSecurityRole(securityRole);
                    logger.info("- Role Authority : {}", securityRole);
                }
                context.addConstraint(securityConstraint);

                SecurityConstraint securityConstraintHealth = new SecurityConstraint();
                securityConstraintHealth.setUserConstraint("NONE");
                securityConstraintHealth.setDisplayName("Nexus Health Access Constraint");
                securityConstraintHealth.setAuthConstraint(true);
                logger.info("Create Security Constraint : {}", securityConstraintHealth.getDisplayName());

                SecurityCollection collectionHealth = new SecurityCollection();
                for (String p : healthPatterns) {
                    collectionHealth.addPattern(p.trim());
                    logger.info("- Path Security : {}", p.trim());
                }
                securityConstraintHealth.addCollection(collectionHealth);

                for (String securityRole : healthRoles) {
                    securityConstraintHealth.addAuthRole(securityRole);
                    context.addSecurityRole(securityRole);
                    logger.info("- Role Authority : {}", securityRole);
                }
                context.addConstraint(securityConstraintHealth);
                logger.info("Programmatic Security Constraints applied (No web.xml found).");

                // Authentication config method (Browser Pop-up)
                LoginConfig loginConfig = new LoginConfig();
                loginConfig.setAuthMethod("BASIC");
                loginConfig.setRealmName("Nexus Backend Realm");
                context.setLoginConfig(loginConfig);

                // Inject now the basic Authentication
                BasicAuthenticator basicAuthenticator = new BasicAuthenticator();
                context.getPipeline().addValve(basicAuthenticator);
            }
        });
    }

    private AccessLogValve getAccessLogValve() {
        AccessLogValve accessLogValve = new AccessLogValve();
        accessLogValve.setDirectory(directory);
        accessLogValve.setPrefix(prefix);
        accessLogValve.setSuffix(suffix);
        accessLogValve.setCheckExists(checkExists);
        accessLogValve.setBuffered(false); /// WARN disable buffer
        accessLogValve.setPattern(pattern);
        accessLogValve.setEncoding(encoding);
        accessLogValve.setAsyncSupported(asyncSupported);
        accessLogValve.setRenameOnRotate(renameOnRotate);
        accessLogValve.setMaxDays(maxDays);
        return accessLogValve;
    }

}
