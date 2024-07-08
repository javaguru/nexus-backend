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

package com.jservlet.nexus.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * SpringBoot Tomcat Container ServletInitializer
 * Only a Spring profile 'withoutTomcat' (or 'withTomcat' with a Tomcat Embedded by SpringBoot!)
 */
@ConditionalOnExpression("#{environment.getProperty('spring.profiles.active').contains('withoutTomcat')}")
public class JServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        // Fix ClassNotFoundException: org.slf4j.impl.StaticLoggerBinder
        System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");

        // Swagger is only available in dev!
        String env = System.getProperty("environment", "development");
        if ("development".equals(env))  System.setProperty("springdoc.swagger-ui.enabled", "true");
        // run app
        return application.sources(Application.class);
    }
}
