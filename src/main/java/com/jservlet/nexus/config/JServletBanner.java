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

package com.jservlet.nexus.config;

import org.springframework.boot.Banner;
import org.springframework.boot.ansi.AnsiBackground;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.PrintStream;
import java.util.MissingResourceException;
import java.util.Properties;

/**
 *  JServlet NexusBackend Banner
 */
@Component
public class JServletBanner implements Banner, ResourceLoaderAware {

    private ResourceLoader resourceLoader;
    private String VERSION;

    @PostConstruct
    public void postConstruct() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:META-INF/version.properties");
        if (!resource.exists())
            throw new MissingResourceException("Unable to find \"version.properties\" on classpath!",
                    getClass().getName(), "version.properties");

        Properties properties = new Properties();
        properties.load(resource.getInputStream());
        VERSION = properties.getProperty("version");
    }

    private final String[] BANNER = { "", // line break!
            "  _  _                       ___            _                  _ \n" +
            " | \\| | ___ ____  _  _  ___ | _ ) __ _  __ | |__ ___  _ _   __| |\n" +
            " | .` |/ -_)\\ \\ /| || |(_-< | _ \\/ _` |/ _|| / // -_)| ' \\ / _` |\n" +
            " |_|\\_|\\___|/_\\_\\ \\_,_| /__/|___/\\__,_|\\__||_\\_\\\\___||_||_|\\__,_| "
    };

    private static final String NEXUS_BACKEND = " :: NexusBackend :: Secure RestApi Backend Gateway";
    private static final int STRAP_LINE_SIZE = 74;

    @Override
    public void printBanner(Environment environment, Class<?> sourceClass, PrintStream printStream) {
        for (String line : BANNER) {
            printStream.println(AnsiOutput.toString(AnsiColor.CYAN, AnsiBackground.DEFAULT, AnsiStyle.BOLD, line));
        }
        String version = " (v" + VERSION + ")";
        StringBuilder padding = new StringBuilder();
        while (padding.length() < STRAP_LINE_SIZE - (version.length() + NEXUS_BACKEND.length())) {
            padding.append(" ");
        }
        printStream.println(AnsiOutput.toString(AnsiColor.GREEN, NEXUS_BACKEND, AnsiColor.DEFAULT, padding.toString(),
                AnsiStyle.FAINT, version));
        printStream.println();
    }

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
