<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" %>
<% request.setCharacterEncoding("UTF-8"); %>
    
<% String exceptionType = (String) request.getAttribute("failedException"); %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>login Authentication Error</title>
    <script src="/wherehouse/js/errorHandler.js" defer></script>
</head>
<body>
    
    <input type="hidden" id="errorType" value="${exceptionType}">
</body>
</html>
