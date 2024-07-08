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

package com.jservlet.nexus.config.web.tomcat;

import org.apache.catalina.valves.ExtendedAccessLogValve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Config for activated the trace in Extended Access Log Valve with an Embedded Tomcat
 */
@ConditionalOnExpression("#{environment.getProperty('spring.profiles.active').contains('withTomcat')}")
@ConditionalOnProperty(value = "nexus.backend.tomcat.accesslog.valve.enable")
@Component
public class TomcatCustomContainer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final static Logger logger = LoggerFactory.getLogger(TomcatCustomContainer.class);

    @Value("${nexus.backend.tomcat.accesslog.directory:/tmp/logs/tomcat-nexus}")
    private String directory;
    @Value("${nexus.backend.tomcat.accesslog.suffix:.log}")
    private String suffix;
    @Value("${nexus.backend.tomcat.accesslog.pattern:date time x-threadname c-ip cs-method cs-uri sc-status bytes x-H(contentLength) time-taken x-H(authType) cs(Authorization) cs(User-Agent)}")
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

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        logger.info("Starting Tomcat Catalina Extended AccessLog Valve");
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
        factory.addContextValves(accessLogValve);
        factory.addEngineValves();
     }
}
