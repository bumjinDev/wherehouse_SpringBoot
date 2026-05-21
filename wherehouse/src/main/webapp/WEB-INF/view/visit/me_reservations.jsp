<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wherehouse - 내 방문 예약</title>
    <link rel="icon" href="/wherehouse/images/home_icon.png">
    <link rel="stylesheet" href="/wherehouse/css/visit_me.css">
</head>
<body>

<script>
    var currentUserId = '<%= request.getAttribute("currentUserId") != null ? request.getAttribute("currentUserId") : "" %>';
    if (currentUserId === '' || currentUserId === 'null') currentUserId = null;
</script>

<header class="vm_header">
    <a href="/wherehouse" class="vm_brand">WhereHouse</a>
    <h1>내 방문 예약</h1>
    <a href="/wherehouse" class="vm_home">홈으로</a>
</header>

<div class="vm_layout">
    <aside class="vm_side">
        <h2>방문 예약 관리</h2>
        <a href="/wherehouse/visit/me/reservations" class="vm_active">내 예약</a>
        <a href="/wherehouse/visit/me/subscriptions">내 구독</a>
        <a href="/wherehouse/visit/me/slots">매물 슬롯 관리</a>
        <a href="/wherehouse/visit/me/notifications">알림</a>
    </aside>

    <main class="vm_main">
        <h2>내 예약 목록</h2>
        <p class="vm_subtitle">탐색자로서 신청한 방문 예약의 전체 이력입니다 (확정·취소·무효화·종료).</p>
        <div id="vm_alerts"></div>
        <div id="vm_list" class="vm_list">
            <div class="vm_empty">불러오는 중...</div>
        </div>
    </main>
</div>

<script src="/wherehouse/js/me_reservations.js"></script>
</body>
</html>
