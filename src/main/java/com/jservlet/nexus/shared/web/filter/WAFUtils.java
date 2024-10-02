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

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * WAF Utilities check for potential evasion inside a query or parameters, headers, cookies and Json Body!:
 * <p>
 * - XSS script injection<br>
 * - SQL injection<br>
 * - Google injection<br>
 * - Command injection<br>
 * - File injection<br>
 * - Link injection<br>
 * </p>
 */
public class WAFUtils {
    /**
     * Potential XSS (script injection) evasion. WAF query, params, headers and cookies!
     */
    public static List<Pattern> xssPattern = new LinkedList<>(List.of(
            // potential XSS (script injection)
            Pattern.compile("(?s)(?i)\\\\u[0-9a-fA-F]{4}|&#x[0-9a-fA-F]{2}|(?:\\x5cx[0-9a-fA-F]{2}|[\"']\\s*+>)|(?:<|&lt;?|%3C|¼|&#0*+60;?|&#x0*+3c;?|\\\\(?:x|u00)3c)!--", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            // |image\/svg\+xml
            Pattern.compile("(?s)(?i)(?:<|&lt;?|%3C|¼|&#0*+60;?|&#x0*+3c;?|\\\\(?:x|u00)3c)(?:script|body|html|table|xss|style|bgsound|portal|picture|fencedframe|template|track|canvas|video|source|audio|object|embed|applet|i?frame|form|input|option|blockquote|area|map|link|base|layer|div|span|img|meta)|on(?:afterprint|beforeprint|beforeunload|hashchange|message|offline|line|pagehide|pageshow|popstate|storage|unload|contextmenu|input|invalid|search|mousewheel|wheel|drag|dragend|dragenter|dragleave|dragover|dragstart|drop|scroll|copy|cut|paste|abort|blur|change|click|dblclick|dragdrop|error|focus|keydown|keypress|keyup|load|mousedown|mousemove|mouseout|mouseover|mouseup|move|reset|resize|select|submit|(?:before)?unload)\\s*+=|\\.cookie|(?:execScript|escape|alert|msgbox|eval|expression)\\s*+\\(|(?:^|\\s+|\\.)(?:this|top|parent|document)\\.[a-zA-Z0-9_%]+|(?:java|vb)script\\s*+:|(?:dyn|low)src|void\\s*+\\(0\\)|http-equiv|text/(?:x-)?scriptlet|fromCharCode|\\.\\s*+href\\s*+=|getElements?By(?:Tag)?(?:Name|Id)\\s*+\\(|\\.\\s*+captureEvents\\s*+\\(|\\.\\s*+create(?:Attribute|Element|TextNode)\\s*+\\(|\\.\\s*+write(?:ln)?\\s*+\\(|\\.\\s*+re(?:place|load)?\\s*+\\(|(?:style|class)\\s*+=|(?:href|src|source|action)\\s*+=\\s*+[\"']|@import|(?:behavior|image|binding)\\s*+:\\s*+url\\s*+\\(|background\\s*+=\\s*+['\"]|AllowScriptAccess\\s*+=|(?:<|&lt;?|%3C|¼|&#0*+60;?|&#x0*+3c;?|\\\\(?:x|u00)3c)(?:\\?|!)\\s*+(?:import|entity|xml)|!\\[CDATA|DATA(?:SRC|FLD|FORMATAS)\\s*+=|Set-?Cookie|new\\s+(ActiveXObject|XMLHttpRequest)\\s*+\\(|schemas-microsoft-com|:namespace|Microsoft\\.XMLHTTP|window\\s*+.\\s*+open\\s*+\\(|\\.\\s*+action\\s*+=|;\\s*+url\\s*+=|acunetix\\s*+web\\s*+vulnerability\\s*+scanner|res://ieframe\\.dll", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    ));
    /**
     * Potential SQL injection evasion. WAF query, params
     */
    public static List<Pattern> sqlPattern = new LinkedList<>(List.of(
            Pattern.compile("(?s)(?i)'\\s*+(?:;|--|#)|;\\s*+--|or\\s+'?1'?\\s*+=\\s*+'?1'?|or\\s+'?0'?\\s*+=\\s*+'?0'?|and\\s+'?1'?\\s*+=\\s*+'?0'?|and\\s+'?0'?\\s*+=\\s*+'?1'?|and\\s+'?1'?\\s*+=\\s*+'?2'?|and\\s+'?2'?\\s*+=\\s*+'?1'?|and\\s+'?1'?\\s*+=\\s*+'?1'?|'\\s*+(?:or|and|where|not|union)\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
            Pattern.compile("(?s)(?i)delete\\s+from|drop\\s+table|create\\s+(?:or\\s+replace\\s+)?(?:table|view|package|index|constraint)|update\\s+.+\\s+set\\s+.+\\s*+=|(?:insert\\s+into\\s+.+(?:\\s+|\\))values|select\\s+.*?(?:\\*|,\\s*+\\w+\\s*+|dummy).*?\\s+from|union.+select.+from|IF\\s*+\\(\\s*+USER\\s*+\\(\\s*+\\)\\s*+LIKE|root@%|BENCHMARK\\s*+\\(\\s*+[0123456789*+-]+\\s*+,\\s*+SHA1\\s*+\\([^)]*+\\)|exec.+[xs]p_|into\\s+(?:out|dump)file|;\\s*+GO\\s+EXEC|cmdshell\\s*+\\(|;\\s*+(?:drop|truncate|delete|insert|select|update|alter|create)\\s+(?:table|view|index|package|constraint|user|trigger|or\\s+replace)|user_name\\s*+\\(\\s*+\\))|LIKE\\s+'%|AND\\s+(?:\\(\\s+)?ROWNUM\\s*+[=<>]|AS\\s+.+\\s+FROM\\s+DUAL", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    ));
    /**
     * Potential google hack
     */
    public static List<Pattern> googlePattern = new LinkedList<>(List.of(
            Pattern.compile("(?s)(?i)google.*?inurl.*?(?:(?:pass|(?:wd|word|wort|list))|admin|(?:up|down)load|sendmail)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    ));
    /**
     * Potential command injection
     */
    public static List<Pattern> commandPattern = new LinkedList<>(List.of(
            Pattern.compile("(?s)(?i)cmd(\\.exe|=dir)|\\.dll|uname|cmdshell|format\\s+[CDE]['\"]|#exec|/bin/ls|;(/usr)?/s?bin/", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    ));
    /**
     * Potential file disclosure
     */
    public static List<Pattern> filePattern = new LinkedList<>(List.of(
            Pattern.compile("(?s)(?i)/(?:var/log|boot|etc|sbin|root)/|etc/(?:passwd|init\\.d|users|groups|hosts)|\\.bash_(?:rc|history)|WS_FTP.LOG|_notes/.+\\.mno|(?:phpinfo|test)\\.php|(?:web|config|ejb-jar|weblogic|citydesk|contribute)\\.xml|\\.ht(?:access|passwd)|httpd\\.conf|conf\\.d|c:[/\\\\](?:windows|system32|boot\\.ini)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    ));
    /**
     * Potential link injection (reframing?)
     */
    public static List<Pattern> linkPattern = new LinkedList<>(List.of(
            Pattern.compile("(?i)mailto:|ftp://", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    ));

    public static boolean isWAFPattern(String value, List<Pattern> patterns) {
        if (value == null) return true;
        // matcher sqlPattern find ?
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).find())
                return false;
        }
        return true;
    }

    public static String stripWAFPattern(String value, List<Pattern> patterns) {
        if (value == null) return null;
        // matcher xssPattern replaceAll ?
        for (Pattern pattern : patterns)
            value = pattern.matcher(value).replaceAll("");
        return value;
    }

    public static void main(String[] args) {
        String test = "image/avif,image/webp,image/apng,image/svg+xml;q=0.8";
        System.out.println(isWAFPattern(test, xssPattern));
    }

}
