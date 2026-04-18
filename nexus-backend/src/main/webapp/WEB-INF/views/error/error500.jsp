<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="en">

<head>
    <title>HTTP ${requestScope['jakarta.servlet.error.status_code']} – Internal Server Error</title>
    <style>
    h1 {
        font-family: Tahoma, Arial, sans-serif;
        color: white;
        background-color: #525D76;
        font-size: 22px;
    }

    h2 {
        font-family: Tahoma, Arial, sans-serif;
        color: white;
        background-color: #525D76;
        font-size: 16px;
    }

    h3 {
        font-family: Tahoma, Arial, sans-serif;
        color: white;
        background-color: #525D76;
        font-size: 14px;
    }

    body {
        font-family: Tahoma, Arial, sans-serif;
        color: black;
        background-color: white;
    }

    b {
        font-family: Tahoma, Arial, sans-serif;
        color: white;
        background-color: #525D76;
    }

    p {
        font-family: Tahoma, Arial, sans-serif;
        background: white;
        color: black;
        font-size: 12px;
    }

    .line {
        height: 1px;
        background-color: #525D76;
        border: none;
    }
    </style>
</head>
<%--jakarta.servlet.error.status_code    java.lang.Integer
jakarta.servlet.error.exception_type java.lang.Class
jakarta.servlet.error.message        java.lang.String
jakarta.servlet.error.exception      java.lang.Throwable
jakarta.servlet.error.request_uri    java.lang.String
jakarta.servlet.error.servlet_name   java.lang.String--%>
<body>

<h1>HTTP ${requestScope['jakarta.servlet.error.status_code']} – Internal Server Error</h1>
<hr class="line"/>
<p><b>Code</b> ${requestScope['jakarta.servlet.error.status_code']}</p>
<p><b>message</b> An Error occurred please contact your system administrator</p>
<p><b>exception</b></p>
<pre>${requestScope['jakarta.servlet.error.exception'].getMessage()}</pre>

</body>
</html>
