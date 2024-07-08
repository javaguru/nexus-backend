<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix='c' uri='http://java.sun.com/jsp/jstl/core' %>
<%@ page trimDirectiveWhitespaces="true"%>

<c:set var="code">${requestScope['javax.servlet.error.status_code']}</c:set>
<c:choose>
    <c:when test="${code == 400}"><c:set var="description">Bad request</c:set></c:when>
    <c:when test="${code == 401}"><c:set var="description">Unauthorized</c:set></c:when>
    <c:when test="${code == 403}"><c:set var="description">Forbidden</c:set></c:when>
    <c:when test="${code == 404}"><c:set var="description">Not found</c:set></c:when>
    <c:when test="${code == 405}"><c:set var="description">Method Not Allowed</c:set></c:when>
    <c:when test="${code == 406}"><c:set var="description">Not Acceptable</c:set></c:when>
    <c:otherwise><c:set var="description">Internal Server Error</c:set></c:otherwise>
</c:choose>

<!doctype html>
<html lang="en">
<head>
    <title>HTTP ${requestScope['javax.servlet.error.status_code']} – ${description}</title>
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
<%--
javax.servlet.error.status_code    java.lang.Integer
javax.servlet.error.exception_type java.lang.Class
javax.servlet.error.message        java.lang.String
javax.servlet.error.exception      java.lang.Throwable
javax.servlet.error.request_uri    java.lang.String
javax.servlet.error.servlet_name   java.lang.String
--%>
<body>

<h1>HTTP ${requestScope['javax.servlet.error.status_code']} – ${description}</h1>
<hr class="line"/>
<%--<p><b>ServletName</b> ${requestScope['javax.servlet.error.servlet_name']}</p>
<p><b>RequestUri</b> ${requestScope['javax.servlet.error.request_uri']}</p>--%>
<p><b>Code</b> ${requestScope['javax.servlet.error.status_code']}</p>
<p><b>Message</b> ${requestScope['javax.servlet.error.message']}</p>
<c:if test="${requestScope['javax.servlet.error.exception'].getMessage() != null}" var="condition">
<p><b>Exception</b> ${requestScope['javax.servlet.error.exception_type']}</p>
<pre>${requestScope['javax.servlet.error.exception'].getMessage()}</pre>
</c:if>

</body>
</html>
