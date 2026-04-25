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

package com.jservlet.nexus.config.web.tomcat.ssl;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ajp.AbstractAjpProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.InetAddress;

/**
 * Config Embedded Tomcat Connector TLS/SSL on the default port 8443 (HTTP/1.1)
 * <br>
 * And Config Embedded Tomcat Connector AJP on the port 8009 with redirection on 8443
 */

@Configuration
@ConditionalOnProperty(value = "nexus.backend.tomcat.connector.https.enable")
@Profile("withTomcat") // Embedded!
public class TomcatConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final static Logger logger = LoggerFactory.getLogger(TomcatConnectorConfig.class);

    @Value("${nexus.backend.tomcat.ssl.keystore-path:/home/root/.keystore}")
    private String pathJKS;
    @Value("${nexus.backend.tomcat.ssl.keystore-password:changeit}")
    private String keyStorePassword;
    @Value("${nexus.backend.tomcat.ssl.certificate.alias:key_server}")
    private String certificateAlias;
    @Value("${nexus.backend.tomcat.ssl.lookups.enable:false}")
    private boolean enableLookups;

    @Value("${nexus.backend.tomcat.ssl.https.port:8443}")
    private int HTTPS_PORT;
    @Value("${nexus.backend.tomcat.ssl.ajp.connector.port:8009}")
    private int AJP_PORT;

    @Value("${nexus.backend.tomcat.ssl.ajp.connector.enable:false}")
    private boolean enableAjp;
    @Value("${nexus.backend.tomcat.ssl.ajp.connector.secretRequired:false}")
    private boolean secretRequiredAjp;
    @Value("${nexus.backend.tomcat.ssl.ajp.connector.protocol:AJP/1.3}")
    private String AJP_PROTOCOL;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        logger.info("Customizing Tomcat Factory with Additional TLS/SSL or AJP Connectors");

        // Add additional connectors strictly once
        if (enableAjp) {
            factory.addAdditionalTomcatConnectors(sslConnector(), ajpConnector());
            logger.info("HTTPS and AJP Connectors successfully added to Tomcat");
        } else {
            factory.addAdditionalTomcatConnectors(sslConnector());
            logger.info("HTTPS Connector successfully added to Tomcat (AJP disabled)");
        }
    }

    /**
     * Helper method to build the AJP Connector.
     * WARNING: Do NOT annotate this with @Bean.
     */
    private Connector ajpConnector() {
        Connector connector = new Connector(AJP_PROTOCOL);
        connector.setPort(AJP_PORT);
        connector.setRedirectPort(HTTPS_PORT);

        AbstractAjpProtocol<?> protocol = (AbstractAjpProtocol<?>) connector.getProtocolHandler();
        protocol.setSecretRequired(secretRequiredAjp);

        // Bind AJP strictly to localhost IPv6 loopback, replicating address="::1"
        try {
            protocol.setAddress(InetAddress.getByName("::1"));
        } catch (Exception e) {
            logger.error("Failed to bind AJP address to ::1", e);
        }

        return connector;
    }

    /**
     * Helper method to build the HTTPS/TLS Connector.
     * WARNING: Do NOT annotate this with @Bean.
     */
    private Connector sslConnector() {
        Connector httpsConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        httpsConnector.setPort(HTTPS_PORT);
        httpsConnector.setSecure(true);
        httpsConnector.setScheme("https");
        httpsConnector.setProperty("SSLEnabled", "true");
        httpsConnector.setEnableLookups(enableLookups);

        // Configure the SSL/TLS Certificate
        SSLHostConfig sslConfig = new SSLHostConfig();
        SSLHostConfigCertificate certConfig = new SSLHostConfigCertificate(sslConfig, SSLHostConfigCertificate.Type.RSA);
        certConfig.setCertificateKeystoreFile(pathJKS);
        certConfig.setCertificateKeystorePassword(keyStorePassword);
        certConfig.setCertificateKeyAlias(certificateAlias);
        sslConfig.addCertificate(certConfig);

        httpsConnector.addSslHostConfig(sslConfig);

        // Attach the shared ThreadPool created in TomcatCustomContainer
        httpsConnector.addLifecycleListener(event -> {
            if (event.getType().equals(org.apache.catalina.Lifecycle.BEFORE_START_EVENT)) {
                org.apache.catalina.Executor sharedExecutor = httpsConnector.getService().getExecutor("tomcatThreadPool");
                if (sharedExecutor != null) {
                    httpsConnector.getProtocolHandler().setExecutor(sharedExecutor);
                    logger.info("HTTPS Connector successfully attached to shared 'tomcatThreadPool'");
                }
            }
        });

        return httpsConnector;
    }
}
