<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!doctype html>
<html lang="en">

<head>
    <title>HTTP ${requestScope['javax.servlet.error.status_code']} – Not Found</title>
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

<body>

<h1>HTTP ${requestScope['javax.servlet.error.status_code']} – Not Found</h1>
<hr class="line"/>
<p><b>Code</b> ${requestScope['javax.servlet.error.status_code']}</p>
<p><b>message</b> Not Found</p>

</body>
</html>
