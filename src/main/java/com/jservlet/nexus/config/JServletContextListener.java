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

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ContextCleanupListener;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 *  NexusBackend ServletContext Listener
 */
@Component
public class JServletContextListener extends ContextCleanupListener {

    private final static Logger logger = LoggerFactory.getLogger(JServletContextListener.class);

    private final JServletBanner jServletBanner;

    public JServletContextListener(JServletBanner jServletBanner) {
         this.jServletBanner = jServletBanner;
    }

    @Override
    public void contextInitialized(@NonNull ServletContextEvent event) {
        logger.info("Starting NexusBackend ServletContext Listener");
        // Banner
        jServletBanner.printBanner(null, null, System.out);

        if (logger.isInfoEnabled()) {
            System.out.println(" Started NexusBackend ServletContext");
            ServletContext servletContext = event.getServletContext();
            ApplicationContext appContext = WebApplicationContextUtils.getWebApplicationContext(event.getServletContext());
            // Server Info
            System.out.println(" ServerInfo: " +
                    AnsiOutput.toString(AnsiColor.GREEN, servletContext.getServerInfo()));
            System.out.println(" ServletApi: " +
                    AnsiOutput.toString(AnsiColor.GREEN, "Specifications v" + servletContext.getMajorVersion() + "."+ servletContext.getMinorVersion()));
            System.out.println();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {

        // Assume SLF4J is bound to logback-classic in the current environment
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();

       /*ClassLoader webAppClassLoader = this.getClass().getClassLoader();
        Class clazz = null;
        try {
            clazz = Class.forName("com..xxx.XMLRequestInterface",false, webAppClassLoader);
        } catch (ClassNotFoundException ignore) {
            //log exception or ignore
        }

        if (clazz != null) {
            if ((clazz.getClassLoader() == webAppClassLoader)) {
                XMLRequestInterface.clearStaticTableCache();
            }
        }*/

        // Close webapp context
        closeWebApplicationContext(event.getServletContext());

        // Call super destroy
        super.contextDestroyed(event);

        // console logs, no more logs!
        System.out.println("Nexus-Backend ServletContext destroyed");
    }

    private void closeWebApplicationContext(ServletContext servletContext) {
        if (servletContext instanceof ConfigurableWebApplicationContext) {
            ((ConfigurableWebApplicationContext) servletContext).close();
        }
    }

}
