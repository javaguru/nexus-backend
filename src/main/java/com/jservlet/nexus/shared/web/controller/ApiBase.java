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

package com.jservlet.nexus.shared.web.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public abstract class ApiBase {

    private final String source;

    public ApiBase(String source) { this.source = source; }

    protected final ResponseEntity<?> getResponseEntity(String code, Exception e) {
        if (e instanceof AccessDeniedException) {
            // let spring security (FilterChain) handle this
            throw (AccessDeniedException) e;
        }
        String cause = e.getCause() != null ? e.getCause().getMessage() : "NA";
        Message message = getMessageObject(code, "ERROR");
        message.setMessage(e.getMessage());
        message.setCause(cause);
        return new ResponseEntity<>(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    protected final ResponseEntity<?> getResponseEntity(String code, String level, String msg, HttpStatus httpStatus) {
        Message message = getMessageObject(code, level);
        message.setMessage(msg);
        return new ResponseEntity<>(message, httpStatus);
    }

    protected final ResponseEntity<?> getResponseEntity(String code, String level, Exception e, HttpStatus httpStatus) {
        Message message = getMessageObject(code, level);
        message.setMessage(e.getMessage());
        return new ResponseEntity<>(message, httpStatus);
    }

    protected final ResponseEntity<?> getResponseEntity(String code, String level, HttpStatus httpStatus) {
        return new ResponseEntity<>(getMessageObject(code, level), httpStatus);
    }

    protected final <T> ResponseEntity<T> getResponseEntity(T object, HttpStatus httpStatus) {
        return new ResponseEntity<>(object, httpStatus);
    }

    protected final <T> ResponseEntity<T> getResponseEntity(HttpStatus httpStatus) {
        return new ResponseEntity<>(httpStatus);
    }

    protected Message getMessageObject(String code, String level) {
        return new Message(code, level, this.source);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected static class Message implements Serializable {

        private static final long serialVersionUID = -5490061086438597077L;

        private String code;
        private String level;
        private String source;
        private String message;
        private String cause;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, String> parameters = new HashMap<>();

        public Message(String code, String level, String source) {
            this.code = code;
            this.level = level;
            this.source = source;
        }

        public Message(String code, String level, String source, String message) {
            this.code = code;
            this.level = level;
            this.source = source;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCause() {
            return cause;
        }

        public void setCause(String cause) {
            this.cause = cause;
        }

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        @Override
        public String toString() {
            return "Message{" +
                    "code='" + code + '\'' +
                    ", level='" + level + '\'' +
                    ", source='" + source + '\'' +
                    ", message='" + message + '\'' +
                    ", cause='" + cause + '\'' +
                    ", parameters=" + parameters +
                    '}';
        }
    }
}
