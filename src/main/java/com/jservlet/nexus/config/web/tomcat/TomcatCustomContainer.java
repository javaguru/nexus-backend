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

package com.jservlet.nexus.config.web.tomcat;

import org.apache.catalina.authenticator.BasicAuthenticator;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.catalina.valves.ExtendedAccessLogValve;
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

/**
 * Custom Container with an Embedded Tomcat: <br>
 *  - Customizes Connector Http <br>
 *  - Extended Access Log Valve <br>
 *  - Error ReportValve <br>
 *  - Configuration ACL / Security constraint <br>
 * <br>
 * <br>
 * server.xml
 * <pre>
 * &lt;Connector port="8080" protocol="HTTP/1.1"
 *             executor="tomcatThreadPool"
 *             redirectPort="8443"
 *             enableLookups="false"
 *             acceptCount="100"
 *             connectionTimeout="20000"
 *             maxPostSize="10485760"
 *             disableUploadTimeout="true"
 *             compression="on"
 *             compressableMimeType="text/html,text/xml,text/plain,text/javascript,text/css,application/json"
 *             URIEncoding="UTF-8"
 *             maxHttpHeaderSize="32768"
 * 			   rejectIllegalHeader="true"
 *             server="git di nexus a"
 *     /&gt;
 * </pre>
 * <pre>
 *      &lt;Valve className="org.apache.catalina.valves.ErrorReportValve"
 *            showReport="false"
 *            showServerInfo="false"/&gt;
 * </pre>
 * <pre>
 *      &lt;Valve className="org.apache.catalina.valves.AccessLogValve" directory="logs"
 *          prefix="localhost_access_log" suffix=".txt"
 *          pattern="%h %l %u %t &quot;%r&quot; %s %b" /&gt;
 * </pre>
 * <br>
 * <br>
 * tomcat-users.xml
 * <pre>
 *      &lt;role rolename="manager-gui"/&gt;
 *      &lt;role rolename="admin-gui"/&gt;
 *      &lt;user username="admin" password="admin" roles="manager-gui,admin-gui"/&gt;
 * </pre>
 */
@Profile("withTomcat")
@Component
public class TomcatCustomContainer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final static Logger logger = LoggerFactory.getLogger(TomcatCustomContainer.class);

    @Value("${nexus.backend.tomcat.accesslog.valve.enable:false}")
    private boolean accessLogEnabled;

    // Default Accesslog properties
    @Value("${nexus.backend.tomcat.accesslog.directory:/tmp/logs/tomcat-nexus}")
    private String directory;
    @Value("${nexus.backend.tomcat.accesslog.suffix:.log}")
    private String suffix;
    @Value("${nexus.backend.tomcat.accesslog.pattern:date time x-threadname c-ip cs-method cs-uri sc-status x-H(locale) x-H(remoteUser) bytes x-H(contentLength) time-taken x-H(authType) cs(Authorization) cs(User-Agent)}")
    private String pattern;
    @Value("${nexus.backend.tomcat.accesslog.encoding:UTF-8}")
    private String encoding;
    @Value("${nexus.backend.tomcat.accesslog.checkExists:true}")
    private boolean checkExists;
    @Value("${nexus.backend.tomcat.accesslog.asyncSupported:true}")
    private boolean asyncSupported;
    @Value("${nexus.backend.tomcat.accesslog.renameOnRotate:true}")
    private boolean renameOnRotate;
    @Value("${nexus.backend.tomcat.accesslog.throwOnFailure:true}")
    private boolean throwOnFailure;
    @Value("${nexus.backend.tomcat.accesslog.maxDays:-1}")
    private int maxDays;

    // Default errorReport properties
    @Value("${nexus.backend.tomcat.error-report.showReport:false}")
    private boolean showReport;
    @Value("${nexus.backend.tomcat.error-report.showServerInfo:false}")
    private boolean showServerInfo;

    // Default Security constraint properties
    @Value("${nexus.backend.tomcat.security.patterns:/actuator/*,/health/*}")
    private String[] securityPatterns;
    @Value("${nexus.backend.tomcat.security.admin.acl.enable:false}")
    private boolean adminAclEnabled;
    @Value("${nexus.backend.tomcat.security.users.file:}")
    private String customUsersFilePath;
    @Value("${nexus.backend.tomcat.security.role:admin-gui}")
    private String securityRole;

    // Default Connector properties (HTTP)
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

        // Configuration Http connector by default
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

            logger.info("Configured Default HTTP Connector: rejectIllegalHeader={}, serverHeader={}, compression={}",
                    rejectIllegalHeader, serverHeader, compression);
        });

        // Access log
        if (accessLogEnabled) {
            logger.info("Starting Tomcat Catalina Extended AccessLog Valve");
            ExtendedAccessLogValve accessLogValve = getExtendedAccessLogValve();
            factory.addContextValves(accessLogValve);
        } else {
            logger.debug("Tomcat AccessLog Valve is disabled");
        }

        // Error report
        ErrorReportValve errorReportValve = new ErrorReportValve();
        errorReportValve.setShowReport(showReport);
        errorReportValve.setShowServerInfo(showServerInfo);
        factory.addEngineValves(errorReportValve); // GLOBAL!
        logger.info("Starting Tomcat Catalina ErrorReport Valve: showReport {} - ShowServerInfo {}", showReport, showServerInfo);

        // Configuration ACL / Security constraint
        if (adminAclEnabled) {
            logger.info("Configuring Tomcat Security Constraint Pattern path");
            factory.addContextCustomizers(context -> {
                MemoryRealm realm = new MemoryRealm();
                File userConfigFile = null;

                try {
                    // specific path
                    if (customUsersFilePath != null && !customUsersFilePath.isEmpty()) {
                        userConfigFile = new File(customUsersFilePath);
                    }

                    // auto-detect catalina.base
                    if ((userConfigFile == null || !userConfigFile.exists()) && System.getProperty("catalina.base") != null) {
                        File candidate = new File(System.getProperty("catalina.base"), "conf/tomcat-users.xml");
                        if (candidate.exists()) {
                            userConfigFile = candidate;
                        }
                    }
                    // auto-detect catalina.home
                    if ((userConfigFile == null || !userConfigFile.exists()) && System.getProperty("catalina.home") != null) {
                        File candidate = new File(System.getProperty("catalina.home"), "conf/tomcat-users.xml");
                        if (candidate.exists()) {
                            userConfigFile = candidate;
                        }
                    }

                    // Application or Fallback Classpath
                    if (userConfigFile != null && userConfigFile.exists()) {
                        logger.info("Loading tomcat-users.xml from path {}", userConfigFile.getAbsolutePath());
                        realm.setPathname(userConfigFile.getAbsolutePath());
                    } else {
                        // File embedded src/main/resources
                        logger.info("Loading embedded tomcat-users.xml from classpath");
                        ClassPathResource resource = new ClassPathResource("tomcat-users.xml");

                        if (resource.exists()) {
                            userConfigFile = File.createTempFile("tomcat-users-embedded", ".xml");
                            userConfigFile.deleteOnExit();

                            try (InputStream inputStream = resource.getInputStream()) {
                                Files.copy(inputStream, userConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            realm.setPathname(userConfigFile.getAbsolutePath()); // IMPORTANT: Ne pas oublier cette ligne
                            logger.info("Embedded tomcat-users.xml extracted to: {}", userConfigFile.getAbsolutePath());
                        } else {
                            logger.error("File tomcat-users.xml not found in classpath!");
                        }
                    }

                } catch (IOException e) {
                    logger.error("Failed to load tomcat-users.xml configuration", e);
                }

                context.setRealm(realm);

                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("NONE");
                securityConstraint.setDisplayName("Admin Access Constraint");
                securityConstraint.setAuthConstraint(true);

                SecurityCollection collection = new SecurityCollection();

                if (securityPatterns != null) {
                    for (String p : securityPatterns) {
                        if (!p.trim().isEmpty()) {
                            collection.addPattern(p.trim());
                            logger.info("- Constraint Pattern {}", p.trim());
                        }
                    }
                }

                securityConstraint.addCollection(collection);
                securityConstraint.addAuthRole(securityRole); // Utilisation de la variable

                context.addConstraint(securityConstraint);
                context.addSecurityRole(securityRole);

                // Authentication config method (Browser Pop-up)
                LoginConfig loginConfig = new LoginConfig();
                loginConfig.setAuthMethod("BASIC"); // ou "DIGEST", "FORM"
                loginConfig.setRealmName("Nexus Backend Realm");
                context.setLoginConfig(loginConfig);

                // Inject now the basic Authentication
                BasicAuthenticator basicAuthenticator = new BasicAuthenticator();
                context.getPipeline().addValve(basicAuthenticator);
            });
        }
    }

    private ExtendedAccessLogValve getExtendedAccessLogValve() {
        ExtendedAccessLogValve accessLogValve = new ExtendedAccessLogValve();
        accessLogValve.setDirectory(directory);
        accessLogValve.setSuffix(suffix);
        accessLogValve.setCheckExists(checkExists);
        accessLogValve.setPattern(pattern);
        accessLogValve.setEncoding(encoding);
        accessLogValve.setAsyncSupported(asyncSupported);
        accessLogValve.setRenameOnRotate(renameOnRotate);
        accessLogValve.setThrowOnFailure(throwOnFailure);
        accessLogValve.setMaxDays(maxDays);
        return accessLogValve;
    }
}
