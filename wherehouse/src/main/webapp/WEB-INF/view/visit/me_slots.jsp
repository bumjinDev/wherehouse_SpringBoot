<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wherehouse - 매물 슬롯 관리</title>
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
    <h1>매물 슬롯 관리</h1>
    <a href="/wherehouse" class="vm_home">홈으로</a>
</header>

<div class="vm_layout">
    <aside class="vm_side">
        <h2>방문 예약 관리</h2>
        <a href="/wherehouse/visit/me/reservations">내 예약</a>
        <a href="/wherehouse/visit/me/subscriptions">내 구독</a>
        <a href="/wherehouse/visit/me/slots" class="vm_active">매물 슬롯 관리</a>
        <a href="/wherehouse/visit/me/notifications">알림</a>
    </aside>

    <main class="vm_main">
        <h2>매물 슬롯 관리</h2>
        <p class="vm_subtitle">등록자로서 자기 매물의 방문 가능 시간 윈도우를 공개·철회하고, 슬롯 현황과 종료된 예약의 방문 결과를 분류합니다.</p>

        <div id="vm_alerts"></div>

        <!-- 매물 미선택 안내 (propertyId 쿼리 미지정 시) -->
        <div id="vm_guide" style="display:none;">
            <div class="vm_alert vm_alert_info">
                관리할 매물을 먼저 선택해야 합니다. <a href="/wherehouse/properties/board" style="color:#1e40af;">매물 게시판</a>에서
                본인이 등록한 매물의 "슬롯 관리" 버튼을 통해 진입하세요. 또는 아래에 매물 식별자를 직접 입력하여 진입할 수 있습니다.
            </div>
            <div class="vm_card">
                <div class="vm_form">
                    <div>
                        <label>매물 식별자 (32자)</label>
                        <input type="text" id="vm_input_property_id" maxlength="32" placeholder="예: a1b2c3d4...">
                    </div>
                    <div>
                        <label>임대 유형</label>
                        <select id="vm_input_lease_type">
                            <option value="">(선택)</option>
                            <option value="CHARTER">전세</option>
                            <option value="MONTHLY">월세</option>
                        </select>
                    </div>
                </div>
                <div style="margin-top:12px;text-align:right;">
                    <button class="vm_btn vm_btn_primary" onclick="window.__vmGo()">조회</button>
                </div>
            </div>
        </div>

        <!-- 매물 선택됨 — 슬롯 관리 화면 -->
        <div id="vm_workspace" style="display:none;">
            <!-- 매물 정보 헤더 -->
            <div class="vm_card" style="background:#f9fafb;">
                <div class="vm_card_head">
                    <div class="vm_card_title">매물 #<span id="vm_h_property_id"></span></div>
                    <div>
                        <button class="vm_btn vm_btn_outline" onclick="window.__vmOpenCreate()">+ 윈도우 공개</button>
                    </div>
                </div>
                <div class="vm_card_grid">
                    <div class="vm_kv"><b>임대 유형 필터</b><span id="vm_h_lease_type">-</span></div>
                </div>
            </div>

            <!-- 윈도우 공개 폼 -->
            <div class="vm_card" id="vm_create_form" style="display:none;margin-top:12px;background:#eff6ff;">
                <div class="vm_card_head">
                    <div class="vm_card_title">새 방문 윈도우 공개</div>
                    <button class="vm_btn vm_btn_subtle" onclick="window.__vmCloseCreate()">닫기</button>
                </div>
                <div class="vm_form">
                    <div>
                        <label>임대 유형</label>
                        <select id="vm_c_lease_type">
                            <option value="CHARTER">전세</option>
                            <option value="MONTHLY">월세</option>
                        </select>
                    </div>
                    <div>
                        <label>슬롯 분할 단위(분)</label>
                        <input type="number" id="vm_c_duration" min="1" max="240" value="30">
                    </div>
                    <div>
                        <label>윈도우 시작 시각</label>
                        <input type="datetime-local" id="vm_c_start">
                    </div>
                    <div>
                        <label>윈도우 종료 시각</label>
                        <input type="datetime-local" id="vm_c_end">
                    </div>
                </div>
                <div style="margin-top:12px;text-align:right;">
                    <button class="vm_btn vm_btn_primary" onclick="window.__vmSubmitCreate()">공개</button>
                </div>
            </div>

            <!-- 슬롯 목록 -->
            <div id="vm_slot_panels" style="margin-top:18px;"></div>
        </div>
    </main>
</div>

<script src="/wherehouse/js/me_slots.js"></script>
</body>
</html>
