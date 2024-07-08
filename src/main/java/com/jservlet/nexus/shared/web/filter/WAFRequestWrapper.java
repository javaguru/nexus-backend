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

package com.jservlet.nexus.shared.web.filter;

import org.apache.commons.io.IOUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.util.*;

/**
 * A class to provide the ability to read a {@link javax.servlet.ServletRequest}'s body multiple times.
 * (Request Parameters can be modifiable!)
 */
public class WAFRequestWrapper extends HttpServletRequestWrapper {

    private ByteArrayOutputStream cachedBytes;
    private Map<String, String[]> modifiableParameters;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request HttpServletRequest
     * @throws IllegalArgumentException if the request is null
     */
    public WAFRequestWrapper(HttpServletRequest request) {
        super(request);
        // WARN Activated the StrictHttpFirewall dependent on call order!
        // copy current ParameterMap!
        this.modifiableParameters = request.getParameterMap();
    }

    /**
     * Set a new ParameterMap
     * @param map   The new map
     */
    public void setParameterMap(Map<String, String[]> map) {
        this.modifiableParameters = map;
    }

    /**
     * Set a new body of bytes
     * @param newRawData byte[]
     */
    public void setInputStream(byte[] newRawData) {
        cachedBytes = new ByteArrayOutputStream(newRawData.length);
        cachedBytes.write(newRawData, 0, newRawData.length);
        //cachedBytes.writeBytes(newRawData);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cachedBytes == null) cacheInputStream();
        return new CachedServletInputStream(cachedBytes.toByteArray());
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    /* Cache the inputStream in order to read it multiple times */
    private void cacheInputStream() throws IOException {
        cachedBytes = new ByteArrayOutputStream();
        IOUtils.copy(super.getInputStream(), cachedBytes);
    }

    /* An inputStream which reads the cached request body */
    private static class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        public CachedServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() throws IOException {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new RuntimeException("Not implemented");
        }
    }

    /* WARN Override some methods HttpRequest */

    public String[] getParameterValues(final String parameter) {
        return modifiableParameters.get(parameter);
    }

    @Override
    public String getParameter(final String parameter) {
        String[] values = modifiableParameters.get(parameter);
        if (values != null) {
            return values[0];
        }
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        // unmodifiable collection cause interface contract
        return Collections.unmodifiableMap(modifiableParameters);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(modifiableParameters.keySet());
    }

}

