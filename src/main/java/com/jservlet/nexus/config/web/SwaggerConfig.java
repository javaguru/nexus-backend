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

package com.jservlet.nexus.config.web;

import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.NonNull;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.MissingResourceException;
import java.util.Properties;

/**
 * Swagger mock-api only available in Dev mode, added in JVM Options: -Denvironment=development
 */
@Conditional(SwaggerConfig.IsDevEnvCondition.class)
@Configuration
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
        //System.getProperties().list(System.out);
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
                .addOpenApiCustomiser(new OpenApiCustomised("api-ui/api-docs.yaml"))
                .build();
    }

    /**
     * OpenApi customised config with the file api-ui/api-docs.yaml
     */
    private class OpenApiCustomised implements OpenApiCustomiser {

        private final String specLocation;

        public OpenApiCustomised(String specLocation) {
            this.specLocation = specLocation;
        }

        @Override
        public void customise(OpenAPI openApi) {

            // Load raw data from file format openapi 3.0.1
            OpenAPI api = new OpenAPIV3Parser().read(specLocation);

            // Keep all the existing schemas..
            Components components = openApi.getComponents();

            // Get the component that we just parse, but not securitySchemes!
            Components componentsApi = api.getComponents();
            if (componentsApi != null) {
                componentsApi.schemas(components.getSchemas());
                componentsApi.headers(components.getHeaders());
                componentsApi.extensions(components.getExtensions());
                componentsApi.callbacks(components.getCallbacks());
                componentsApi.links(components.getLinks());
                //componentsApi.securitySchemes(components.getSecuritySchemes());

                // Set the customised Components Security Schemes
                openApi.setComponents(componentsApi);
            }

            // Set the customised Security, see https://swagger.io/docs/specification/authentication/
            openApi.setSecurity(api.getSecurity());

            // Apply the info now!
            openApi.setInfo(apiInfo());

            // not, in all case, let the scan package do it for us!
            //openApi.setPaths(api.getPaths());

            // not used!
            openApi.setExtensions(api.getExtensions());
            openApi.setExternalDocs(api.getExternalDocs());
            openApi.setServers(api.getServers());
            openApi.setTags(api.getTags());
        }
    }


    private Info apiInfo() {
        return new Info()
                .title("Mock Test API")
                .description( "The Mock Test Api Nexus Backend Application\n" +
                                "- 1 Test GET, POST, PUT, PATCH, DELETE\n" +
                                "- 2 Test POST, PUT, PATCH file\n" +
                                "- 3 Test Error 400, 401 and 500\n" +
                                "- 4 Test Security GET and POST XSS data\n")
                .version(VERSION)
                .contact(new Contact().name("Franck Andriano.").email("franck@jservlet.com"))
                .license(new License().name("Copyright (c) JServlet.com").url("https://jservlet.com"));
    }

   static class IsDevEnvCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
            final String env = context.getEnvironment().getProperty("environment");
            if (env == null || env.isEmpty()) {
                return false;
            }
            return env.contains("development");
        }
    }

}
