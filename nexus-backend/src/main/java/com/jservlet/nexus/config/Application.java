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

package com.jservlet.nexus.config;

import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 *  SpringBoot Application
 */

//@OpenAPIDefinition(servers = {@Server(url = "/nexus-backend", description = "Default Server URL")})
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@EnableCaching
@ServletComponentScan  // Scan all @WebListener, @WebServlet or @WebFilter!
@ComponentScan("com.jservlet.nexus") // Scan the base package for all components
@Import({
        ApplicationConfig.class
})
public class Application {

    /**
     * -Denvironment=development -Dserver.servlet.context-path=/nexus-backend -Dserver.port=8082
     *
     * @param args {@link String[]}
     */
    public static void main(String[] args) {
        // Swagger is only available in dev!
        String env = System.getProperty("environment", "development");
        if ("development".equals(env))  System.setProperty("springdoc.swagger-ui.enabled", "true");
        // Server path and port
        System.setProperty("server.servlet.context-path", System.getProperty("server.servlet.context-path", "/nexus-backend"));
        System.setProperty("server.port", System.getProperty("server.port", "8082"));
        new SpringApplicationBuilder(Application.class).bannerMode(Banner.Mode.CONSOLE).run(args);
    }

}
