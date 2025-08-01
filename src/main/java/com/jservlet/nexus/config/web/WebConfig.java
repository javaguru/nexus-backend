/*
 * Copyright (C) 2001-2025 JServlet.com Franck Andriano.
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
import com.jservlet.nexus.shared.web.interceptor.RateLimitInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.lang.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/*
 * Web App Configuration
 */
@Configuration
@ComponentScan({
        "com.jservlet.nexus.controller",
        // Scan from shared server components
        "com.jservlet.nexus.shared.web.controller",
        "com.jservlet.nexus.shared.web.filter",
        "com.jservlet.nexus.shared.web.listener"
})
public class WebConfig implements ApplicationContextAware {

    private final static Logger logger = LoggerFactory.getLogger(WebConfig.class);

    private static ApplicationContext appContext;

    private final Environment env;

    private final ObjectMapper objectMapper;

    public WebConfig(Environment env, ObjectMapper objectMapper) {
        this.env = env;
        this.objectMapper = objectMapper;
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


    @Value("${nexus.backend.interceptor.ratelimit.refillToken:1000}")
    private int refillToken;
    @Value("${nexus.backend.interceptor.ratelimit.refillMinutes:1}")
    private int refillMinutes;
    @Value("${nexus.backend.interceptor.ratelimit.bandwidthCapacity:1000}")
    private int bandwidthCapacity;

    /**
     * Rate Limit interceptor checks incoming requests against a rate limit defined on a per-IP-address basis.
     * @return RateLimitInterceptor
     */
    @Bean
    @ConditionalOnProperty(value = "nexus.api.backend.interceptor.ratelimit.enabled")
    public RateLimitInterceptor limitInterceptor() {
        RateLimitInterceptor limitInterceptor = new RateLimitInterceptor();
        limitInterceptor.setObjectMapper(objectMapper);
        limitInterceptor.setRefillToken(refillToken);
        limitInterceptor.setRefillMinutes(refillMinutes);
        limitInterceptor.setBandwidthCapacity(bandwidthCapacity);
        return limitInterceptor;
    }
}
