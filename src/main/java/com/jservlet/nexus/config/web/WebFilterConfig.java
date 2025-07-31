package com.jservlet.nexus.config.web;

import com.github.ziplet.filter.compression.CompressingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.ForwardedHeaderFilter;

import javax.servlet.Filter;
import javax.servlet.ServletContext;

/*
 * Web Filter Chain Configuration
 */
@Configuration
public class WebFilterConfig implements ServletContextAware {

    private ServletContext servletContext;
    private final Environment env;

    @Value("${nexus.backend.filter.forwardedHeader.removeOnly:true}")
    private boolean forwardedHeaderRemoveOnly;

    @Autowired
    public WebFilterConfig(Environment env) {
        this.env = env;
    }

    @Override
    public void setServletContext(@NonNull ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /**
     * Forwarded Header Filter, see rfc7239
     * RemoveOnly at true, discard and ignore forwarded headers
     *
     * @return Forwarded Filter Bean
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(value="nexus.backend.filter.forwardedHeader.enabled")
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
    @ConditionalOnProperty(value="nexus.backend.filter.gzip.enabled")
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

}
