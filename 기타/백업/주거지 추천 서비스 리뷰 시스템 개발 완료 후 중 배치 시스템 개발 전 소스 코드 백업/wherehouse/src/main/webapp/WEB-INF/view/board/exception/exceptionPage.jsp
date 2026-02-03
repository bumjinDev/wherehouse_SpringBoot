<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <title>${stateCode} Error</title>
    <style>
        body {
            background-color: #f9f9f9;
            font-family: 'Segoe UI', sans-serif;
            color: #333;
            margin: 0;
            padding: 0;
        }
        .container {
            margin-left: 5vw;
            margin-top: 10vh;
        }
        .status {
            font-size: 5rem;
            font-weight: bold;
            color: #d9534f;
        }
        .message {
            font-size: 1.5rem;
            margin-top: 10px;
        }
    </style>
</head>
<body>
<div class="container">
    <div class="status">${stateCode}</div>
    <div class="message">
        <c:choose>
            <c:when test="${stateCode == '403'}">
                작성자만 접근 가능한 페이지입니다. 권한이 없습니다.
            </c:when>
            <c:when test="${stateCode == '404'}">
                존재하지 않는 게시글에 대한 잘못된 접근입니다.
            </c:when>
            <c:otherwise>
                예기치 않은 오류가 발생했습니다.
            </c:otherwise>
        </c:choose>
    </div>
</div>
</body>
</html>
