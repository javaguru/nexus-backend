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

import com.github.ziplet.filter.compression.CompressingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.filter.*;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

/*
 * Web Mvc Configuration
 */
@Configuration
@Import(SwaggerConfig.class)
@ComponentScan({
        "com.jservlet.nexus.controller",
        // Scan from shared server components
        "com.jservlet.nexus.shared.web.controller",
        "com.jservlet.nexus.shared.web.filter",
        "com.jservlet.nexus.shared.web.listener"
})
@EnableWebMvc
//@MultipartConfig(location="/tmp", fileSizeThreshold=1024*1024, maxFileSize=1024*1024*15, maxRequestSize=1024*1024*15)
public class WebConfig implements WebMvcConfigurer, ResourceLoaderAware, ServletContextAware, ApplicationContextAware {

    private final static Logger logger = LoggerFactory.getLogger(WebConfig.class);


    private ResourceLoader resourceLoader;

    private ServletContext servletContext;

    private static ApplicationContext appContext;

    private Environment env;
    private static final String ENV_VAR = "environment";

    @Autowired
    public void setEnv(Environment env) {
        this.env = env;
    }


    @Override
    public synchronized void setApplicationContext(@NonNull ApplicationContext ac) {
        logger.info("SpringBoot set ApplicationContext -> ac: ['{}']", ac.getId());
        WebConfig.appContext = ac;

        if (logger.isInfoEnabled()) {
            Map<String, Object> map = getApplicationProperties(env);
            // Debug also log system out!
            logger.info("*** SpringBoot ApplicationProperties ***");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                logger.info("{} = {}", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Get the current ApplicationContext
     * @return ApplicationContext
     */
    public static synchronized ApplicationContext getApplicationContext() {
        return appContext;
    }

    /* Stuff, get loaded Application Properties */
    private static Map<String, Object> getApplicationProperties(Environment env) {
        Map<String, Object> map = new LinkedHashMap<>(); // keep order!
        if (env instanceof ConfigurableEnvironment) {
            for (PropertySource<?> propertySource : ((ConfigurableEnvironment) env).getPropertySources()) {
                // WARN all tracked springboot config file *.properties!
                if (propertySource instanceof OriginTrackedMapPropertySource) {
                    for (String key : ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
                        map.put(key, propertySource.getProperty(key));
                    }
                }
            }
        }
        return map;
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
    public void configureViewResolvers(ViewResolverRegistry registry) {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");
        registry.viewResolver(viewResolver);
    }

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    /*
     * For Mock only!
     * WARN Neutralised Converters for receive a native ByteArray conversion Json from Jackson and not only a content in Base64!
      @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new StringHttpMessageConverter(UTF_8));
        converters.add(new FormHttpMessageConverter());
        converters.add(new ResourceHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
    }*/

    /*@Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error").setViewName("error");
        registry.addViewController("/forbidden").setViewName("forbidden");
        registry.addViewController("/notfound").setViewName("notfound");
    }*/


    @Value("${nexus.backend.filter.forwardedHeader.removeOnly:true}")
    private boolean forwardedHeaderRemoveOnly;

    /**
     * Forwarded Header Filter, see rfc7239
     * RemoveOnly at true, discard and ignore forwarded headers
     *
     * @return Forwarded Filter Bean
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(value="nexus.backend.filter.forwardedHeader.enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> forwardedInfoFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        ForwardedHeaderFilter filter = new ForwardedHeaderFilter();
        filter.setRemoveOnly(forwardedHeaderRemoveOnly);
        registrationBean.setFilter(filter);
        registrationBean.setOrder(1);
        return registrationBean;
    }

    /**
     * Generally use gzip for all resources !
     *
     * @return a gzip implementation (only response)
     */
    @Bean
    @Order(2)
    @ConditionalOnProperty(value="nexus.backend.filter.gzip.enabled", havingValue = "true")
    public FilterRegistrationBean<Filter> gzipFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CompressingFilter());
        registrationBean.setOrder(2);
        return registrationBean;
    }


    /**
     * The full logs request with payload!
     *
     * @return The Logging filter
     */
    @Bean
    public CommonsRequestLoggingFilter logFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setIncludeHeaders(true);
        filter.setHeaderPredicate(header -> !header.equalsIgnoreCase("authorization"));
        filter.setMaxPayloadLength(10000);
        filter.setBeforeMessagePrefix("INCOMING REQUEST : ");
        filter.setAfterMessagePrefix("OUTGOING REQUEST : ");
        filter.setServletContext(servletContext);
        filter.setEnvironment(env);
        return filter;
    }

    /**
     * Springboot only, WebServerFactory register Servlet (and Jsp!)
     * @return the factory
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> enableDefaultServlet() {
        return (factory) -> factory.setRegisterDefaultServlet(true);
    }

    /**
     * Use Servlet 4 style MultipartResolver: StandardServletMultipartResolver <br>
     * Strict Servlet compliance only multipart/form-data
     * @return the MultipartResolver,
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver() {
            private final Set<HttpMethod> SUPPORTED_METHODS = EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);
            @Override
            public boolean isMultipart(@NonNull HttpServletRequest request) {
                if (!SUPPORTED_METHODS.contains(HttpMethod.resolve(request.getMethod()))) return false;
                return StringUtils.startsWithIgnoreCase(request.getContentType(), MediaType.MULTIPART_FORM_DATA_VALUE);
            }
        };
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        if ("development".equals(env.getProperty(ENV_VAR))) {
            registry.addResourceHandler("/swagger-ui/**").resourceChain(false);
        }

        // Enable static resources
        registry.addResourceHandler("/resources/api-ui/**").addResourceLocations("/resources/");
        registry.addResourceHandler("/static/**").addResourceLocations("/static/");
    }


    @Value("${nexus.backend.content.negotiation.favorParameter:false}")
    private boolean favorParameter;
    @Value("${nexus.backend.content.negotiation.parameterName:mediaType}")
    private String parameterName;

    @Value("${nexus.backend.content.negotiation.ignoreAcceptHeader:false}")
    private boolean ignoreAcceptHeader;

    @Value("${nexus.backend.content.negotiation.useRegisteredExtensionsOnly:true}")
    private boolean useRegisteredExtensionsOnly;

    @Value("${nexus.backend.content.negotiation.commonMediaTypes:true}")
    private boolean commonMediaTypes;


    /**
     * Configure a default ContentNegotiation with a default HeaderContentNegotiationStrategy (ignoreAcceptHeader at false)
     * Force the defaultContentType by application/octet-stream
     * @see org.springframework.web.accept.ContentNegotiationManagerFactoryBean
     *
     * @param configurer The current ContentNegotiationConfigurer
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer // JSON Mandatory forced for Resource 404 not found from the Backend
                .defaultContentType(MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM)
                //.defaultContentTypeStrategy(new HeaderContentNegotiationStrategy())
                .favorParameter(favorParameter)
                .parameterName(parameterName)
                .ignoreAcceptHeader(ignoreAcceptHeader)
                .useRegisteredExtensionsOnly(useRegisteredExtensionsOnly)
                .mediaType("pdf", MediaType.APPLICATION_PDF); // Add pdf as safe extensions by default!

        if (commonMediaTypes) {
            extractedMediaTypes(configurer, "classpath:mime/MediaTypes_commons.properties");
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void extractedMediaTypes(ContentNegotiationConfigurer configurer, String classpath) {
        Resource resource = resourceLoader.getResource(classpath);
        if (resource.exists()) {
            try {
                Properties properties = new Properties();
                properties.load(resource.getInputStream());
                properties.forEach((key, value) ->
                        configurer.mediaType(key.toString(), MediaType.parseMediaType(value.toString())));
            } catch (IOException e) {
                logger.error("Failed to load '{}' media types {}", classpath, e.getMessage());
            }
        }
    }

}
