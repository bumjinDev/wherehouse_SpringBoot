<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wherehouse - 내 재개방 알림 구독</title>
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
    <h1>내 재개방 알림 구독</h1>
    <a href="/wherehouse" class="vm_home">홈으로</a>
</header>

<div class="vm_layout">
    <aside class="vm_side">
        <h2>방문 예약 관리</h2>
        <a href="/wherehouse/visit/me/reservations">내 예약</a>
        <a href="/wherehouse/visit/me/subscriptions" class="vm_active">내 구독</a>
        <a href="/wherehouse/visit/me/slots">매물 슬롯 관리</a>
        <a href="/wherehouse/visit/me/notifications">알림</a>
    </aside>

    <main class="vm_main">
        <h2>재개방 알림 구독 목록</h2>
        <p class="vm_subtitle">슬롯이 취소로 다시 열릴 경우 알림을 받을 수 있도록 신청한 구독입니다. 구독은 예약을 보장하지 않습니다.</p>
        <div id="vm_alerts"></div>
        <div id="vm_list" class="vm_list">
            <div class="vm_empty">불러오는 중...</div>
        </div>
    </main>
</div>

<script src="/wherehouse/js/me_subscriptions.js"></script>
</body>
</html>
