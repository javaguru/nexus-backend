package com.jservlet.nexus.config.web;

import com.jservlet.nexus.shared.web.interceptor.RateLimitInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/*
 * Web Mvc Configuration
 */
@Configuration
@EnableWebMvc
@Import(SwaggerConfig.class)
public class WebMvcConfig implements WebMvcConfigurer, ResourceLoaderAware {

    private final static Logger logger = LoggerFactory.getLogger(WebMvcConfig.class);
    private ResourceLoader resourceLoader;

    private final Environment env;
    private static final String ENV_VAR = "environment";

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(Optional<RateLimitInterceptor> rateLimitInterceptor, Environment env) {
        this.rateLimitInterceptor = rateLimitInterceptor.orElse(null);
        this.env = env;
    }

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        }
    }

    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/WEB-INF/views/");
        viewResolver.setSuffix(".jsp");
        registry.viewResolver(viewResolver);
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        if ("development".equals(env.getProperty(ENV_VAR))) {
            registry.addResourceHandler("/swagger-ui/**").resourceChain(false);
        }
        registry.addResourceHandler("/resources/api-ui/**").addResourceLocations("/resources/");
        registry.addResourceHandler("/static/**").addResourceLocations("/static/");
    }

    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
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

    /**
     * Springboot only, WebServerFactory register Servlet (and Jsp!)
     * @return the factory
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> enableDefaultServlet() {
        return (factory) -> factory.setRegisterDefaultServlet(true);
    }

}
