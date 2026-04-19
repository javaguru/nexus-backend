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

package com.jservlet.nexus.config.web.env;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

/**
 * Nexus EnvironmentPostProcessor manages the properties of the Nexus Backend app, uses the Ordered + 1 interface
 * <p>
 * - Read 1st class path resource [settings.properties] <br>
 * - Read Global file ${user.home}/conf-global/config.properties <br>
 * - Read Global file ${user.home}/conf/config.properties <br>
 * - Read file ${user.home}/cfg/${spring.application.name}/config.properties
 */
public class NexusEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    // Used to load classpath resources (e.g., classpath:settings.properties)
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        System.out.println("[NEXUS-ENV] Starting NexusEnvironmentPostProcessor");

        MutablePropertySources propertySources = environment.getPropertySources();

        // Load settings.properties from classpath
        Resource settingsResource = resourceLoader.getResource("classpath:settings.properties");
        loadIfPresent(propertySources, settingsResource);

        // Load external files
        String userHome = environment.resolvePlaceholders("${user.home}");

        // Global files
        loadIfPresent(propertySources, new FileSystemResource(userHome + "/conf-global/config.properties"));
        loadIfPresent(propertySources, new FileSystemResource(userHome + "/conf/config.properties"));

        // Specific application context
        String appName = environment.getProperty("spring.application.name");
        System.out.println("[NEXUS-ENV] spring.application.name = " + appName);

        if (appName != null) {
            loadIfPresent(propertySources, new FileSystemResource(userHome + "/cfg/" + appName + "/config.properties"));
        } else {
            System.out.println("[NEXUS-ENV] WARNING: spring.application.name is null. The specific file will not be loaded.");
        }
    }

    private void loadIfPresent(MutablePropertySources propertySources, Resource resource) {
        if (resource.exists()) {
            try {
                // addFirst est correct ici, le dernier fichier chargé aura la priorité absolue
                propertySources.addFirst(new ResourcePropertySource(resource));
                System.out.println("[NEXUS-ENV] Loaded successfully: " + resource.getDescription());
            } catch (IOException e) {
                System.out.println("[NEXUS-ENV] Read error for : " + resource.getDescription() + " - " + e.getMessage());
            }
        } else {
            System.out.println("[NEXUS-ENV] File ignored (not found) : " + resource.getDescription());
        }
    }

    @Override
    public int getOrder() {
        // Crucial: We tell Spring to execute this processor RIGHT AFTER
        // read the standard application.properties/yml files.
        // This way, spring.application.name will be available, and we won't get overwritten.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
