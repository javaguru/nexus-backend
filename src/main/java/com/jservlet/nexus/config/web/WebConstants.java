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

/*
 * Web Constants
 */
public interface WebConstants {
    /**
     * CONSTANTS RESPONSE
     */
    String $200 = "200";
    String $201 = "201";
    String $204 = "204";

    String $400 = "400";
    String $401 = "401";
    String $403 = "403";
    String $404 = "404";
    String $405 = "405";
    String $415 = "415";
    String $422 = "422";

    String $500 = "500";

    String REQ_SUCCESSFULLY = "Request executed successfully, returning the requested item(s)";
    String REQ_NOT_CORRECTLY = "Request is not formed correctly";
    String USER_NOT_AUTH = "User not authenticated";
    String INTERNAL_SERVER = "Internal server error, see error code and documentation for more details";

    String PERM_DENIED = "Permission denied for this resource";
    String HTTP_NOT_ALLOWED = "HTTP Method (GET, POST, ...) not allowed for this resource";
    String REQ_SUCC_EMPTY_RESP = "Request executed successfully, returning an empty response";
    String ITEM_NOT_FOUND = "Requested item not found, see error code and documentation for more details";
    String PERMISSION_DENIED = "Permission denied for this resource";
    String UNSUPPORTED_MEDIA_TYPE = "Unsupported Media Type";
    String VALIDATION_ERROR = "Error while validating request's content, see error code and documentation for more details";

}
