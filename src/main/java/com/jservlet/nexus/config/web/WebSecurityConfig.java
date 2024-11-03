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

import com.jservlet.nexus.shared.web.filter.WAFPredicate;
import com.jservlet.nexus.shared.web.security.WebHttpFirewall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/*
 * Web Security Configuration: HttpFirewall, FilterChain and Customizer
 */
@Configuration
@EnableWebSecurity //(debug = true)
public class WebSecurityConfig {

    private final static Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

    private final ServletContext servletContext;

    @Value("${nexus.spring.web.security.debug:false}")
    private boolean webSecurityDebug;

    @Autowired
    public WebSecurityConfig(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    // WARN no UserDetailsServiceAutoConfiguration


    /**
     * Performs some WebSecurity customizations:
     * - debug
     * - httpFirewall
     * - servletContext
     * @param webHttpFirewall The HttpFirewall
     * @return A WebSecurity customizer!
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer(HttpFirewall webHttpFirewall) {
        return (web) -> web.debug(webSecurityDebug).httpFirewall(webHttpFirewall)
                .setServletContext(servletContext);
    }

    /**
     * SecurityFilterChain
     *
     * @param http The HttpSecurity
     * @throws Exception An exception
     * @return A WebSecurity customizer!
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // cors config, csrf disable
        http.cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable);
        // session STATELESS
        http.sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // authorize HttpRequests
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/", "/mock/**", "/static/**",
                                "/actuator/**", "/swagger-ui/index.html",
                                "/api/**").permitAll());
        // security headers
        http.headers(headers -> {
              headers.contentTypeOptions();
              headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin);
              headers.cacheControl();
              headers.xssProtection();
              headers.httpStrictTransportSecurity();
        });
        return http.build();
    }


    /**
     * Spring EL reads allow method delimited with a comma and splits into a List of Strings
     */
    // Provide a List of HttpMethods
    @Value("#{'${nexus.backend.security.cors.allowedHttpMethods:GET,POST,PUT,HEAD,DELETE,PATCH}'.split(',')}")
    private List<String> allowedCorsHttpMethods;
    // Provide a Regex Patterns domains
    @Value("#{'${nexus.backend.security.cors.allowedOriginPatterns:}'.split(',')}")
    private String allowedOriginPatterns;
    // Provide a List of domains
    @Value("#{'${nexus.backend.security.cors.allowedOrigins:*}'.split(',')}")
    private List<String> allowedOrigins;
    // Headers: Authorization,Cache-Control,Content-Type
    @Value("#{'${nexus.backend.security.cors.allowedHeaders:}'.split(',')}")
    private List<String> allowedHeaders;
    // At true Origin cannot be a wildcard '*' a list of domains need to be provided.
    @Value("${nexus.backend.security.cors.credentials:false}")
    private boolean credentials;
    // Headers: Authorization
    @Value("#{'${nexus.backend.security.cors.exposedHeaders:}'.split(',')}")
    private List<String> exposedHeaders;
    // Max age in second
    @Value("${nexus.backend.security.cors.maxAge:3600}")
    private Long maxAgeCors;

    /**
     * CORS Security configuration, allow Control Request by Headers <br>
     * Access-Control-Allow-Origin: * <br>
     * Access-Control-Allow-Methods: GET,POST,PUT,HEAD,DELETE,PATCH <br>
     * Access-Control-Max-Age: 3600 <br>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        if (!allowedOriginPatterns.isEmpty()) configuration.addAllowedOriginPattern(allowedOriginPatterns);
        if (!allowedOrigins.isEmpty()  && !Objects.equals(allowedOrigins.get(0), "")) {
            configuration.setAllowedOrigins(allowedOrigins);
        }
        if (!allowedCorsHttpMethods.isEmpty()  && !Objects.equals(allowedCorsHttpMethods.get(0), "")) {
            configuration.setAllowedMethods(allowedCorsHttpMethods);
        }
        // setAllowCredentials(true) is important, otherwise:
        // The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' when the request's credentials mode is 'include'.
        configuration.setAllowCredentials(credentials);
        // setAllowedHeaders is important! Without it, OPTIONS preflight request will fail with 403 Invalid CORS request
        if (!allowedHeaders.isEmpty() && !Objects.equals(allowedHeaders.get(0), "")) {
            configuration.setAllowedHeaders(allowedHeaders);  // "Authorization", "Cache-Control", "Content-Type"
        }
        if (!exposedHeaders.isEmpty() && !Objects.equals(exposedHeaders.get(0), "")) {
            configuration.setExposedHeaders(exposedHeaders); // "Authorization"
        }
        configuration.setMaxAge(maxAgeCors);
        // register source Cors pattern
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }


    @Value("${nexus.backend.security.allowSemicolon:false}")
    public boolean isAllowSemicolon;
    @Value("${nexus.backend.security.allowUrlEncodedSlash:false}")
    public boolean isAllowUrlEncodedSlash;
    @Value("${nexus.backend.security.allowUrlEncodedDoubleSlash:false}")
    public boolean isAllowUrlEncodedDoubleSlash;
    @Value("${nexus.backend.security.allowUrlEncodedPeriod:false}")
    public boolean isAllowUrlEncodedPeriod;
    @Value("${nexus.backend.security.allowBackSlash:false}")
    public boolean isAllowBackSlash;
    @Value("${nexus.backend.security.allowNull:false}")
    public boolean isAllowNull;
    @Value("${nexus.backend.security.allowUrlEncodedPercent:false}")
    public boolean isAllowUrlEncodedPercent;
    @Value("${nexus.backend.security.allowUrlEncodedCarriageReturn:false}")
    public boolean isAllowUrlEncodedCarriageReturn;
    @Value("${nexus.backend.security.allowUrlEncodedLineFeed:false}")
    public boolean isAllowUrlEncodedLineFeed;
    @Value("${nexus.backend.security.allowUrlEncodedParagraphSeparator:false}")
    public boolean isAllowUrlEncodedParagraphSeparator;
    @Value("${nexus.backend.security.allowUrlEncodedLineSeparator:false}")
    public boolean isAllowUrlEncodedLineSeparator;

    /**
     * SpEL reads allow method delimited with a comma and splits into a List of Strings
     */
    @Value("#{'${nexus.backend.security.allowedHttpMethods:GET,POST,PUT,OPTIONS,HEAD,DELETE,PATCH}'.split(',')}")
    private List<String> allowedHttpMethods;

    /**
     * Web HttpFirewall, allow semicolon by example
     *
     * @return HttpFirewall
     */
    @Bean
    public HttpFirewall webHttpFirewall() {
        WebHttpFirewall firewall = new WebHttpFirewall();

        firewall.setAllowedHttpMethods(allowedHttpMethods);

        firewall.setAllowSemicolon(isAllowSemicolon);
        firewall.setAllowUrlEncodedSlash(isAllowUrlEncodedSlash);
        firewall.setAllowUrlEncodedDoubleSlash(isAllowUrlEncodedDoubleSlash);
        firewall.setAllowUrlEncodedPeriod(isAllowUrlEncodedPeriod);
        firewall.setAllowBackSlash(isAllowBackSlash);
        firewall.setAllowNull(isAllowNull);
        firewall.setAllowUrlEncodedPercent(isAllowUrlEncodedPercent);
        firewall.setAllowUrlEncodedCarriageReturn(isAllowUrlEncodedCarriageReturn);
        firewall.setAllowUrlEncodedLineFeed(isAllowUrlEncodedLineFeed);
        firewall.setAllowUrlEncodedParagraphSeparator(isAllowUrlEncodedParagraphSeparator);
        firewall.setAllowUrlEncodedLineSeparator(isAllowUrlEncodedLineSeparator);

        WAFPredicate waf = wafPredicate();

        // Predicate Parameter Name/Value
        firewall.setAllowedParameterNames(waf.getWAFParameterNames());
        firewall.setAllowedParameterValues(waf.getWAFParameterValues());

        // Predicate Header Name/Value
        firewall.setAllowedHeaderNames(waf.getWAFHeaderNames());
        firewall.setAllowedHeaderValues(waf.getWAFHeaderValues());

        // Predicate Hostnames
        firewall.setAllowedHostnames(waf.getWAFHostnames());

        return firewall;
    }

    @Bean
    public WAFPredicate wafPredicate() {
         return new WAFPredicate();
    }

    /**
     * A simple implementation of {@link RequestRejectedHandler} that sends an error  with configurable status code
     * @return RequestRejectedHandler
     */
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return new HttpStatusRequestRejectedHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, RequestRejectedException requestRejectedException) throws IOException {
                String message = (String) request.getAttribute("javax.servlet.error.message");
                String uri = (String) request.getAttribute("javax.servlet.error.request_uri");
                // WARN path /error can be reject cause can also contained the reject parameter..
                if (message != null && uri != null) {
                    logger.error("Rejecting request due to: {} uri: {}", requestRejectedException.getMessage(), uri);
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // msg never displayed !?
            }
        };
    }
}
