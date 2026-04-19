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

package com.jservlet.nexus.config.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.parser.OpenAPIV3Parser;

import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.customizers.OpenApiCustomizer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.NonNull;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.MissingResourceException;
import java.util.Properties;

@Configuration
@ConditionalOnProperty(name = "environment", havingValue = "development")
public class SwaggerConfig implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    private String VERSION;

    @PostConstruct
    public void postConstruct() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:META-INF/version.properties");
        if (!resource.exists())
            throw new MissingResourceException("Unable to find \"version.properties\" on classpath!",
                    getClass().getName(), "version.properties");
        Properties properties = new Properties();
        properties.load(resource.getInputStream());
        VERSION = properties.getProperty("version");
    }

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public GroupedOpenApi mockOpenApi() {
        return GroupedOpenApi.builder()
                .group("mock-api")
                .packagesToScan("com.jservlet.nexus.controller")
                .addOpenApiCustomizer(new OpenApiCustomized("api-ui/api-docs.yaml"))
                .build();
    }

    /**
     * OpenApi load customized Security Schemes config, see file api-ui/api-docs.yaml
     */
    private class OpenApiCustomized implements OpenApiCustomizer {

        private final String specLocation;

        public OpenApiCustomized(String specLocation) {
            this.specLocation = specLocation;
        }

        @Override
        public void customise(OpenAPI openApi) {

            // Load raw data from file format openapi 3.0.1
            OpenAPI api = new OpenAPIV3Parser().read(specLocation);

            // Keep all current schemas scanned by @ServletComponentScan
            Components components = openApi.getComponents();

            // Create a component Api from current openApi
            Components componentsApi = api.getComponents();
            if (componentsApi != null && components != null) {
                componentsApi.schemas(components.getSchemas());
                componentsApi.headers(components.getHeaders());
                componentsApi.extensions(components.getExtensions());
                componentsApi.callbacks(components.getCallbacks());
                componentsApi.links(components.getLinks());
                //componentsApi.securitySchemes(components.getSecuritySchemes());

                // Set the customized Components without Security Schemes
                openApi.setComponents(componentsApi);
            }

            // Set the customized Security Schemes from api-ui/api-docs.yaml
            // see https://swagger.io/docs/specification/authentication/
            openApi.setSecurity(api.getSecurity());

            // Apply the info now!
            openApi.setInfo(apiInfo());

            //openApi.setExtensions(api.getExtensions());
            //openApi.setExternalDocs(api.getExternalDocs());
            //openApi.setServers(api.getServers());
            //openApi.setTags(api.getTags());
        }
    }

    private Info apiInfo() {
        return new Info()
                .title("Mock Test API")
                .description("""
                        The Mock Test Api Nexus Backend Application
                        - 1 Test Get, Post, PUT
                        - 2 Test Post and Put file
                        - 3 Test Error 400, 401 and 500
                        - 4 Test Security Get and Post XSS data
                        """)
                .version(VERSION)
                .contact(new Contact().name("Franck Andriano.").email("franck@jservlet.com"))
                .license(new License().name("Copyright (c) JServlet.com").url("https://jservlet.com"));
    }
}
