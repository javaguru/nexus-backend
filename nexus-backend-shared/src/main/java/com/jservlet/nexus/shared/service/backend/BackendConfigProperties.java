package com.jservlet.nexus.shared.service.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BackendConfigProperties {

    @Value("${nexus.backend.url}")
    private String backendUrl;

    @Value("${nexus.backend.header.authorization.username:}")
    private String username;

    @Value("${nexus.backend.header.authorization.password:}")
    private String password;

    @Value("${nexus.backend.header.cookie:}")
    private String cookie;

    @Value("${nexus.backend.header.bearer:}")
    private String bearer;

    @Value("${nexus.backend.header.remove:true}")
    private boolean removeHeaders;

    @Value("${nexus.backend.forwarded.headers:true}")
    private boolean forwardedHeaders;

    @Value("#{'${nexus.backend.forwarded.client.headers:}'.split(',')}")
    private List<String> forwardedClientHeaders = new ArrayList<>();

    @Value("${nexus.backend.header.host.remove:false}")
    private boolean removeHostHeader;

    @Value("${nexus.backend.header.origin.remove:false}")
    private boolean removeOriginHeader;

    @Value("${nexus.backend.http.response.truncated:false}")
    private boolean truncated;

    @Value("${nexus.backend.http.response.truncated.maxLength:1000}")
    private int maxLengthTruncated = 1000;

    @Value("${nexus.backend.header.user-agent:JavaNexus}")
    private String userAgent;

    @Value("${nexus.backend.error.message.class:com.jservlet.nexus.shared.service.backend.api.ErrorMessage}")
    private String errorMessageClassName = "com.jservlet.nexus.shared.service.backend.api.ErrorMessage";

    public String getBackendUrl() {
        return backendUrl;
    }

    public void setBackendUrl(String backendUrl) {
        this.backendUrl = backendUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getCookie() {
        return cookie;
    }

    public String getBearer() {
        return bearer;
    }

    public boolean isRemoveHeaders() {
        return removeHeaders;
    }

    public boolean isForwardedHeaders() {
        return forwardedHeaders;
    }

    public List<String> getForwardedClientHeaders() {
        return forwardedClientHeaders;
    }

    public boolean isRemoveHostHeader() {
        return removeHostHeader;
    }

    public boolean isRemoveOriginHeader() {
        return removeOriginHeader;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int getMaxLengthTruncated() {
        return maxLengthTruncated;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getErrorMessageClassName() {
        return errorMessageClassName;
    }

    public void setErrorMessageClassName(String errorMessageClassName) {
        this.errorMessageClassName = errorMessageClassName;
    }
}
