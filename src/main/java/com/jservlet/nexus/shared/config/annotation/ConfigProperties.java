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

package com.jservlet.nexus.shared.config.annotation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.web.context.ServletContextAware;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.lang.annotation.*;
import java.util.*;

/**
 * Annotation for interfaces declaring a config properties file as PropertySourcesPlaceholder
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ConfigProperties.ConfigPropertiesRegistrar.class)
public @interface ConfigProperties {

    String[] value();

    class ConfigPropertiesRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            Class<?>  annotationType = ConfigProperties.class;
            AnnotationAttributes annotationAttributes = AnnotationAttributes.fromMap(
              importingClassMetadata.getAnnotationAttributes(annotationType.getName(), false));
              Assert.notNull(annotationAttributes, String.format("@%s is not present on import class '%s' as expected",
                    annotationType.getSimpleName(), importingClassMetadata.getClassName()));
            // Register property sources placeholder configurer
            registry.registerBeanDefinition("nexusPropertySourcesPlaceHolderConfigurer",
                    BeanDefinitionBuilder.rootBeanDefinition(NexusPropertySourcesPlaceHolderConfigurer.class)
                    .addPropertyValue("projectConfigProperties", annotationAttributes.getStringArray("value"))
                    .getBeanDefinition());
        }
    }

    class NexusPropertySourcesPlaceHolderConfigurer extends PropertySourcesPlaceholderConfigurer
        implements InitializingBean, ResourceLoaderAware, ServletContextAware, ApplicationContextAware {
        // Set Logger ConfigProperties!
        private static final Logger logger = LoggerFactory.getLogger(ConfigProperties.class);

        private String[] projectConfigProperties;

        private Environment environment;

        private ResourceLoader resourceLoader;

        private ServletContext servletContext;

        private ApplicationContext applicationContext;

        public void setProjectConfigProperties(String[] projectConfigProperties) {
            this.projectConfigProperties = projectConfigProperties;
        }

        @Override
        public void setEnvironment(@NonNull Environment environment) {
            //set system properties!
            super.setEnvironment(environment);
            this.environment = environment;
        }

        @Override
        public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
             this.resourceLoader = resourceLoader;
        }

        @Override
        public void setServletContext(@NonNull ServletContext servletContext) {
            this.servletContext = servletContext;
        }

        @Override
        public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }

        @Override
        public void afterPropertiesSet() throws Exception {
            MutablePropertySources propertySources = new MutablePropertySources();
            // Required resources!
            List<Resource> requiredSources = new ArrayList<>(projectConfigProperties.length);
            for (String configProperty : projectConfigProperties) {
                requiredSources.add(resourceLoader.getResource(configProperty));
            }
            addPropertySource(propertySources, requiredSources, true);
            // Existing resources ?
            List<Resource> listResources = new ArrayList<>(Arrays.asList(
                    new FileSystemResource(environment.resolveRequiredPlaceholders(
                            "${user.home}/conf-global/config.properties")),
                    new FileSystemResource(environment.resolveRequiredPlaceholders(
                            "${user.home}/conf/config.properties"))));
            // servletContext available!
            if (servletContext != null) {
                Resource resource = new FileSystemResource(environment.resolveRequiredPlaceholders(
                        "${user.home}/cfg" + servletContext.getContextPath() + "/config.properties"));
                listResources.add(resource);
            }
            else // or getId applicationContext, see spring.application.name
            if (applicationContext != null) {
                Resource resource = new FileSystemResource(environment.resolveRequiredPlaceholders(
                        "${user.home}/cfg/" + applicationContext.getId() + "/config.properties"));
                listResources.add(resource);
            }
            addPropertySource(propertySources, listResources, false);
            setPropertySources(propertySources);

            // logged all the keys/values
            if (logger.isInfoEnabled()) {
                Map<String, Object> map = new TreeMap<>(); // alpha order!
                for (PropertySource<?> propertySource : propertySources) {
                    if (propertySource instanceof EnumerablePropertySource) {
                        final String[] propertyNames = ((EnumerablePropertySource<?>) propertySource).getPropertyNames();
                        for (String propertyName : propertyNames) {
                            map.put(propertyName, propertySource.getProperty(propertyName));
                        }
                    }
                }
                logger.info("*** Loaded ConfigProperties ***");
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    logger.info("{} = {}", entry.getKey(), entry.getValue());
                }
            }
        }

        private void addPropertySource(MutablePropertySources propertySources, List<Resource> resources, boolean required)
                throws IOException {
            for (Resource resource : resources) {
                if (required || resource.exists()) {
                    logger.info("Load properties from: {}", resource);
                    propertySources.addFirst(new ResourcePropertySource(resource));
                } else {
                    logger.info("Resource doesn't exist: {}", resource);
                }
            }
        }


    }
}
