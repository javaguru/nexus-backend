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

package com.jservlet.nexus.shared.exceptions;

/**
 * Thrown when a method is called which use the current logged in user.
 */
public class NexusNotAuthorizedException extends Exception {

    public NexusNotAuthorizedException(String message) { super(message); }
    public NexusNotAuthorizedException(String message, Throwable cause) { super(message, cause); }
    public NexusNotAuthorizedException(Throwable cause) { super(cause); }

}
