package com.jservlet.nexus.config.web.env;

import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

public class NexusEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(NexusEnvironmentPostProcessor.class);

    // Used to load classpath resources (e.g., classpath:settings.properties)
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
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
        if (appName != null) {
            loadIfPresent(propertySources, new FileSystemResource(userHome + "/cfg/" + appName + "/config.properties"));
        }
    }

    private void loadIfPresent(MutablePropertySources propertySources, Resource resource) {
        if (resource.exists()) {
            try {
                // addLast prevents overwriting command line arguments or application.properties
                propertySources.addFirst(new ResourcePropertySource(resource));
                logger.info("Loaded config: " + resource.getDescription());
            } catch (IOException e) {
                logger.error("Failed to load: " + resource.getDescription());
            }
        }
    }
}
