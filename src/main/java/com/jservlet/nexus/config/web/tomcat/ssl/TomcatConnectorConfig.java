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

package com.jservlet.nexus.config.web.tomcat.ssl;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ajp.AbstractAjpProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Config Embedded Tomcat Connector TLS/SSL on the default port 8443 (HTTP/1.1)
 * <br>
 * And Config Embedded Tomcat Connector AJP on the port 8009 with redirection on 8443
 * <br>
 * Only a Spring profile 'withTomcat' (with a Tomcat Embedded by SpringBoot!)
 * <br>
 * <pre>
 *      &lt;Connector SSLEnabled="true" acceptCount="100" clientAuth="false"
 *                disableUploadTimeout="true" enableLookups="false" maxThreads="25"
 *                port="8443" keystoreFile="/home/root/.keystore"
 *                keystorePass="changeit"
 *                protocol="org.apache.coyote.http11.Http11NioProtocol" scheme="https"
 *                secure="true" sslProtocol="TLS" /&gt;
 * </pre>
 * <pre>
 *      &lt;Connector port="8009" protocol="AJP/1.3" redirectPort="8443"/&gt;
 * </pre>
 */
@ConditionalOnExpression("#{environment.getProperty('spring.profiles.active').contains('withTomcat')}")
@ConditionalOnProperty(value = "nexus.backend.tomcat.connector.https.enable")
@Configuration
public class TomcatConnectorConfig {

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

    @Bean
    public ServletWebServerFactory servletContainer() {
        logger.info("Starting TLS/SSL Tomcat Factory");
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
        if (enableAjp) tomcat.addAdditionalTomcatConnectors(sslConnector(), ajpConnector());
        else tomcat.addAdditionalTomcatConnectors(sslConnector());
        return tomcat;
    }

    private Connector ajpConnector() {
        // Define an AJP 1.3 Connector on port 8009
        Connector connector = new Connector(AJP_PROTOCOL);
        connector.setPort(AJP_PORT);
        connector.setRedirectPort(HTTPS_PORT);
        // Disabled required secret
        ((AbstractAjpProtocol<?>) connector.getProtocolHandler()).setSecretRequired(secretRequiredAjp);
        return connector;
    }

    private Connector sslConnector(){
        // Connector Apache Coyote Http11NioProtocol
        Connector httpsConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        httpsConnector.setPort(HTTPS_PORT);
        httpsConnector.setSecure(true);
        httpsConnector.setScheme("https");
        httpsConnector.setProperty("SSLEnabled", "true");
        httpsConnector.setEnableLookups(enableLookups);
        logger.info("Protocol: {}", httpsConnector.getProtocol());

        // Now create a SSLHostConfig with the Root RSA Certificate
        SSLHostConfig sslConfig = new SSLHostConfig();
        SSLHostConfigCertificate certConfig = new SSLHostConfigCertificate(sslConfig, SSLHostConfigCertificate.Type.RSA);
        certConfig.setCertificateKeystoreFile(pathJKS);
        certConfig.setCertificateKeystorePassword(keyStorePassword);
        certConfig.setCertificateKeyAlias(certificateAlias);
        sslConfig.addCertificate(certConfig);

        // Add the SSLHostConfig in the Tomcat Connector
        httpsConnector.addSslHostConfig(sslConfig);

        return httpsConnector;
    }
}
