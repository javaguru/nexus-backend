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

package com.jservlet.nexus.controller;

import com.jservlet.nexus.shared.service.backend.BackendServiceImpl.*;
import com.jservlet.nexus.shared.web.controller.ApiBase;
import io.swagger.v3.oas.annotations.Hidden;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/*
 * The Default Error Controller
 */
@Hidden
@Controller
public class DefaultErrorController extends ApiBase implements ErrorController { // Thank Phillip!

    private final ErrorAttributes errorAttributes;

    private static final String SOURCE = "ERROR-REST-NEXUS-BACKEND";

    @Autowired
    public DefaultErrorController(ErrorAttributes errorAttributes) {
        super(SOURCE);
        Assert.notNull(errorAttributes, "ErrorAttributes must not be null");
        this.errorAttributes = errorAttributes;
    }

    @ResponseBody
    @RequestMapping(value = {"/error"}, method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> error(HttpServletRequest request) {
        Map<String, Object> body = getErrorAttributes(request, getTraceParameter(request));
        final HttpStatus status = getStatus(request);
        return super.getResponseEntity(String.valueOf(status.value()), "ERROR", String.valueOf(body.get("message")), status);
    }

    private boolean getTraceParameter(HttpServletRequest request) {
        String parameter = request.getParameter("trace");
        return StringUtils.isNotBlank(parameter) && !"false".equalsIgnoreCase(parameter);
    }

    private Map<String, Object> getErrorAttributes(HttpServletRequest request, boolean includeStackTrace) {
        WebRequest webRequest = new ServletWebRequest(request);
        ErrorAttributeOptions options = ErrorAttributeOptions.defaults();
        if (includeStackTrace) {
            options.including(ErrorAttributeOptions.Include.STACK_TRACE);
        }
        return this.errorAttributes.getErrorAttributes(webRequest, options);
    }

    private HttpStatus getStatus(HttpServletRequest request) {
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        if (statusCode != null) {
            try {
                return HttpStatus.valueOf(statusCode);
            } catch (Exception ignore) {
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }


    /*@GetMapping(path = "/error")
    public String error() {
        return "error/error500"; // error is also a 404 with SpringBoot !?
    }*/

    @GetMapping(path = "/notfound")
    public String notfound() {
        return "error/error404";
    }
    @GetMapping(path = "/forbidden")
    public String forbidden() {
        return "error/error403";
    }

}
