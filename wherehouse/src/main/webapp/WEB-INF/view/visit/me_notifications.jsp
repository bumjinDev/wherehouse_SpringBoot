<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wherehouse - 방문 예약 알림</title>
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
    <h1>방문 예약 알림</h1>
    <a href="/wherehouse" class="vm_home">홈으로</a>
</header>

<div class="vm_layout">
    <aside class="vm_side">
        <h2>방문 예약 관리</h2>
        <a href="/wherehouse/visit/me/reservations">내 예약</a>
        <a href="/wherehouse/visit/me/subscriptions">내 구독</a>
        <a href="/wherehouse/visit/me/slots">매물 슬롯 관리</a>
        <a href="/wherehouse/visit/me/notifications" class="vm_active">알림</a>
    </aside>

    <main class="vm_main">
        <h2>방문 예약 관련 알림</h2>
        <p class="vm_subtitle">슬롯 예약 확정, 예약 무효화, 슬롯 재개방 등 본인에게 도달한 통지 목록입니다.</p>

        <div style="margin-bottom:14px;">
            <label style="font-size:13px;color:#374151;">
                <input type="checkbox" id="vm_unread_only" onchange="window.__vmReload()"> 미읽음만 보기
            </label>
        </div>

        <div id="vm_alerts"></div>
        <div id="vm_list" class="vm_list">
            <div class="vm_empty">불러오는 중...</div>
        </div>
        <div class="vm_loadmore">
            <button class="vm_btn vm_btn_outline" id="vm_load_more" onclick="window.__vmLoadMore()" style="display:none;">더 보기</button>
        </div>
    </main>
</div>

<script src="/wherehouse/js/me_notifications.js"></script>
</body>
</html>
