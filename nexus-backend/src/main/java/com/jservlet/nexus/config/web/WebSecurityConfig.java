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

package com.jservlet.nexus.config.web;

import com.jservlet.nexus.shared.service.monitor.NmtMonitorService;
import com.jservlet.nexus.shared.service.security.ml.RequestAnalyzerService;
import com.jservlet.nexus.shared.web.filter.WAFPredicate;
import com.jservlet.nexus.shared.web.security.WebHttpFirewall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.DispatcherType;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/*
 * Web Security Configuration: HttpFirewall, FilterChain and Customizer
 */
@Configuration
@EnableWebSecurity(debug = true)
public class WebSecurityConfig {

    private final static Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

    private final ServletContext servletContext;

    @Value("${nexus.spring.web.security.debug:false}")
    private boolean webSecurityDebug;

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

    @Value("${nexus.spring.web.security.csp.policy:default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none';}")
    private String cspPolicyDirectives;

    @Value("${nexus.spring.web.security.hsts.maxAgeInSeconds:31536000}")
    private long maxAgeInSeconds;
    @Value("${nexus.spring.web.security.hsts.includeSubDomains:false}")
    private boolean includeSubDomains;

    @Value("${nexus.spring.web.security.referrer.policy:NO_REFERRER}")
    private String referrerPolicy;

    /**
     * SecurityFilterChain
     *
     * @param http The HttpSecurity
     * @param corsConfigurationSource  The Cors config
     * @throws Exception An exception
     * @return SecurityFilterChain A WebSecurity customizer!
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource) throws Exception {
        // cors config, csrf disable
        //http.cors(Customizer.withDefaults())
        http.cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable);

        // session STATELESS
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // authorize HttpRequests
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers(
                        "/",                // Internal index page
                        "/health/status",   // Internal Health status page
                        "/nmt/admin/monitor/nmt",   // Internal Health Mmt Monitor page
                        "/mock/**",         // Internal Dev Mock page
                        "/static/**",       // static image svg
                        "/actuator/**",     // Actuator
                        "/api/**",          // Rest Api Backend
                        "/error",           // Error page
                        "/swagger-ui/**",   // Swagger UI v3
                        "/v3/api-docs/**"   // Swagger Docs v3
                ).permitAll()
                .anyRequest().authenticated() // Bonne pratique : sécuriser tout le reste par défaut
                //.anyRequest().permitAll() // Temporarily permit all requests for debugging
        );

        // security headers
        http.headers(headers -> {
            // Les options par défaut utilisent maintenant Customizer.withDefaults()
            headers.contentTypeOptions(Customizer.withDefaults());
            headers.cacheControl(Customizer.withDefaults());

            // Configuration des frames
            headers.frameOptions(frame -> frame.sameOrigin());

            // XSS Protection (Nécessite la déclaration explicite de la valeur dans la nouvelle API)
            headers.xssProtection(xss ->
                    xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
            );

            // CSP
            if (!cspPolicyDirectives.isEmpty()) {
                headers.contentSecurityPolicy(csp -> csp
                        .policyDirectives(cspPolicyDirectives)
                );
            }

            // HSTS
            headers.httpStrictTransportSecurity(hsts -> {
                hsts.maxAgeInSeconds(maxAgeInSeconds);
                hsts.includeSubDomains(includeSubDomains);
            });

            // Referrer-Policy
            headers.referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.valueOf(referrerPolicy))
            );
        });

        return http.build();
    }


    /**
     * Spring EL reads allow method delimited with a comma and splits into a List of Strings (or an empty List)
     */
    // Provide a List of HttpMethods
    @Value("#{T(java.util.Arrays).asList('${nexus.backend.security.cors.allowedHttpMethods:GET,POST,PUT,OPTION,HEAD,DELETE,PATCH}')}")
    private List<String> allowedCorsHttpMethods;
    // Provide a Regex Patterns domains
    @Value("${nexus.backend.security.cors.allowedOriginPatterns:#{null}")
    private String allowedOriginPatterns;
    // Provide a List of domains
    @Value("#{T(java.util.Arrays).asList('${nexus.backend.security.cors.allowedOrigins:*}')}")
    private List<String> allowedOrigins;
    // Headers: Authorization,Cache-Control,Content-Type,X-Requested-With,Accept
    @Value("#{T(java.util.Arrays).asList('${nexus.backend.security.cors.allowedHeaders:Authorization,Cache-Control,Content-Type,X-Requested-With,Accept}')}")
    private List<String> allowedHeaders;
    // At true Origin cannot be a wildcard '*' a list of domains need to be provided.
    @Value("${nexus.backend.security.cors.credentials:false}")
    private boolean credentials;
    // Headers: Authorization
    @Value("#{T(java.util.Arrays).asList('${nexus.backend.security.cors.exposedHeaders:}')}")
    private List<String> exposedHeaders;
    // Max age in second
    @Value("${nexus.backend.security.cors.maxAge:3600}")
    private Long maxAgeCors;

    /*
     * CORS Security configuration, allow Control Request by Headers <br>
     * Access-Control-Allow-Origin: * <br>
     * Access-Control-Allow-Methods: GET,POST,PUT,OPTION,HEAD,DELETE,PATCH <br>
     * Access-Control-Max-Age: 3600 <br>
     * Test OPTIONS request on the local domain:
     * <br>
     * curl -v -H "Access-Control-Request-Method: GET" -H "Origin: http://localhost:8082" -X OPTIONS http://localhost:8082/nexus-backend/api/get
     * or -H "Origin: http://localhost:4200"
     */
    @Bean("corsConfigurationSource")
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        if (allowedOriginPatterns != null && !allowedOriginPatterns.isEmpty()) configuration.addAllowedOriginPattern(allowedOriginPatterns);
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) configuration.setAllowedOrigins(allowedOrigins);
        if (allowedCorsHttpMethods != null && !allowedCorsHttpMethods.isEmpty()) configuration.setAllowedMethods(allowedCorsHttpMethods);
        // setAllowCredentials(true) is important, otherwise:
        // The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' when the request's credentials mode is 'include'.
        configuration.setAllowCredentials(credentials);
        // setAllowedHeaders is important! Without it, OPTIONS preflight request will fail with 403 Invalid CORS request
        if (allowedHeaders != null && !allowedHeaders.isEmpty()) configuration.setAllowedHeaders(allowedHeaders);  // "Authorization", "Cache-Control", "Content-Type"
        if (exposedHeaders != null && !exposedHeaders.isEmpty()) configuration.setExposedHeaders(exposedHeaders); // "Authorization"
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

    @Value("${nexus.backend.security.predicate.parameterNamesLength:255}")
    private int parameterNamesLength = 255;
    @Value("${nexus.backend.security.predicate.parameterValuesLength:1000000}")
    private int parameterValuesLength = 1000000;
    @Value("${nexus.backend.security.predicate.headerNamesLength:255}")
    private int headerNamesLength = 255;
    @Value("${nexus.backend.security.predicate.headerNamesValuesLength:25000}")
    private int headerNamesValuesLength = 25000;
    @Value("${nexus.backend.security.predicate.hostNamesLength:255}")
    private int hostNamesLength = 255;
    @Value("${nexus.backend.security.predicate.hostName.pattern:}") // "^(www\\.)?nexus\\.jservlet\\.com(:[0-9]+)?$"
    private String hostNamesPattern;
    @Value("${nexus.backend.security.predicate.userAgent.blocked:false}")
    private boolean userAgentBlocked;
    @Value("${nexus.backend.security.predicate.aiUserAgent.blocked:true}")
    private boolean aiUserAgentBlocked;

    @Bean
    public WAFPredicate wafPredicate() {
        WAFPredicate wafPredicate = new WAFPredicate();
        wafPredicate.setParameterNamesLength(parameterNamesLength);
        wafPredicate.setParameterValuesLength(parameterValuesLength);
        wafPredicate.setHeaderNamesLength(headerNamesLength);
        wafPredicate.setHeaderValuesLength(headerNamesValuesLength);
        wafPredicate.setHostNamesLength(hostNamesLength);

        if (hostNamesPattern != null && !hostNamesPattern.isEmpty()) {
            wafPredicate.setAllowedHostnames(Pattern.compile(hostNamesPattern,Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
        }

        wafPredicate.setBlockDisallowedUserAgents(userAgentBlocked);
        wafPredicate.setBlockDisallowedAIUserAgents(aiUserAgentBlocked);

        return wafPredicate;
    }

    /**
     * Web HttpFirewall, allow semicolon by example
     * @param waf WAFPredicate
     * @return HttpFirewall
     */
    @Bean
    public HttpFirewall webHttpFirewall(WAFPredicate waf) {
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


        // WARN We are not applying any predicate rules here, let the WAFFilter do that for us!
       /* // Predicate Parameter Name/Value
        firewall.setAllowedParameterNames(waf.getWAFParameterNames());
        firewall.setAllowedParameterValues(waf.getWAFParameterValues());

        // Predicate Header Name/Value
        firewall.setAllowedHeaderNames(waf.getWAFHeaderNames());
        firewall.setAllowedHeaderValues(waf.getWAFHeaderValues());

        // Predicate Hostnames
        firewall.setAllowedHostnames(waf.getWAFHostnames());*/

        return firewall;
    }

    /**
     * RequestAnalyzer Service LLM ONNX
     * @return RequestAnalyzerService
     */
    @Bean
    public RequestAnalyzerService analyzerService() {
        return new RequestAnalyzerService();
    }

    /**
     * NmtMonitor Service
     * @return NmtMonitorService
     */
    @Bean
    public NmtMonitorService nmtMonitorService() {
        return new NmtMonitorService();
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
                String message = (String) request.getAttribute("jakarta.servlet.error.message");
                String uri = (String) request.getAttribute("jakarta.servlet.error.request_uri");
                // WARN path /error can be reject cause can also contained the reject parameter..
                if (message != null && uri != null) {
                    logger.error("Rejecting request due to: {} uri: {}", requestRejectedException.getMessage(), uri);
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, message); // msg never displayed !?
            }
        };
    }

}
