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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ziplet.filter.compression.CompressingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.nio.charset.StandardCharsets.UTF_8;

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
@SuppressWarnings({ "unchecked", "rawtypes" })
public class WebConfig implements WebMvcConfigurer, ResourceLoaderAware, ServletContextAware, ApplicationContextAware {

    private final static Logger logger = LoggerFactory.getLogger(WebConfig.class);

    private static final String ENV_VAR = "environment";
    private Environment env;

    private ResourceLoader resourceLoader;
    private ServletContext servletContext;

    private static ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public void setEnv(Environment env) {
        this.env = env;
    }

    @Override
    public synchronized void setApplicationContext(@NonNull ApplicationContext ac) {
        logger.info("SpringBoot set ApplicationContext -> ac: ['{}']", ac.getId());
        WebConfig.context = ac;

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
    public synchronized ApplicationContext getApplicationContext() {
        return context;
    }

    /* Stuff, get loaded Application Properties */
    private static Map<String, Object> getApplicationProperties(Environment env) {
        Map<String, Object> map = new TreeMap<>(); // alpha order!
        if (env instanceof ConfigurableEnvironment) {
            for (PropertySource<?> propertySource : ((ConfigurableEnvironment) env).getPropertySources()) {
                // propertySource instanceof EnumerablePropertySource
                if (propertySource instanceof OriginTrackedMapPropertySource) {
                    if ("Config resource 'class path resource [application.properties]' via location 'optional:classpath:/'"
                            .equals(propertySource.getName())) {
                        for (String key : ((EnumerablePropertySource) propertySource).getPropertyNames()) {
                            map.put(key, propertySource.getProperty(key));
                        }
                        break;
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

    @Override // for Mock only!
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Note that the order matters here! If the StringHttpMessageConverter is add after the jsonConverter
        // the documentation JSON is returned as a giant string instead of a (valid) JSON object
        converters.add(new StringHttpMessageConverter(UTF_8));
        converters.add(new ResourceHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error").setViewName("error");
        registry.addViewController("/forbidden").setViewName("forbidden");
        registry.addViewController("/notfound").setViewName("notfound");
    }


    /**
     * Forwarded Header Filter, see rfc7239
     *
     * @return Forwarded Filter Bean
     */
    @Bean
    @Order(1)
    public FilterRegistrationBean forwardedInfoFilter() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(new ForwardedHeaderFilter());
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
    public FilterRegistrationBean gzipFilter() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setFilter(new CompressingFilter());
        registrationBean.setOrder(2);
        return registrationBean;
    }

    /**
     * Cors Filter, Allowed method, headers and origin
     *
     * @return The Cors Filter
     */
    @Bean
    @Order(3)
    public FilterRegistrationBean corsFilterRegistrationBean() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
        registrationBean.setFilter(corsFilter());
        registrationBean.setOrder(3);
        return registrationBean;
    }

    @Bean
    public Filter corsFilter() {
        return new CorsFilter(request -> {
            final CorsConfiguration configuration = new CorsConfiguration();
            configuration.addAllowedMethod("*");
            configuration.addAllowedHeader("*");
            configuration.setAllowCredentials(true);
            configuration.addAllowedOriginPattern("*");
            return configuration;
        });
    }

    /**
     * Http Method Override Filter
     *
     * @return The Method Override

    @Order(4)
    @Bean
    public FilterRegistrationBean httpMethodOverrideFilterRegistrationBean() {
        FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
        registrationBean.setFilter(new HTTPMethodOverrideFilter());
        registrationBean.setOrder(4);
        return registrationBean;
    }*/

    /**
     * Listener RequestId
     * @return a Listener RequestId

    @Bean
    public ServletListenerRegistrationBean<ServletRequestListener> servletRequestId() {
        return new ServletListenerRegistrationBean<>(new RequestIdServletRequestListener());
    }*/

    /**
     * The full logs request and response
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
        filter.setAfterMessagePrefix("OUTGOING RESPONSE : ");
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
     * Use Servlet 4 style MultipartResolver: StandardServletMultipartResolver
     * @return the MultipartResolver
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver() {
            @Override
            public boolean isMultipart(@NonNull HttpServletRequest request) {
                if (!"POST".equalsIgnoreCase(request.getMethod()) && !"PUT".equalsIgnoreCase(request.getMethod())) {
                    return false;
                }
                String contentType = request.getContentType();
                return contentType != null && contentType.toLowerCase().startsWith("multipart/");
            }
        };
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        if ("development".equals(env.getProperty(ENV_VAR))) {
            registry.addResourceHandler("/swagger-ui/**")
                    .addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/")
                    .resourceChain(false);
        }

        // Enable static resources
        registry.addResourceHandler("/resources/api-ui/**").addResourceLocations("/resources/");
        registry.addResourceHandler("/static/**").addResourceLocations("/static/");
    }

}
