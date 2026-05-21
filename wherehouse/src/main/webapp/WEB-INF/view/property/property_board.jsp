<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wherehouse - 매물 게시판</title>
    <link rel="stylesheet" href="/wherehouse/css/property_board.css">
    <!-- 방문 예약 슬롯 선택 모달(공통) -->
    <link rel="stylesheet" href="/wherehouse/css/visit_slot_picker.css">
    <link rel="icon" href="/wherehouse/images/home_icon.png">
</head>
<body>

<script>
    var currentUserId = '<%= request.getAttribute("currentUserId") != null ? request.getAttribute("currentUserId") : "" %>';
    if (currentUserId === '' || currentUserId === 'null') currentUserId = null;
</script>

<!-- 헤더 -->
<header id="pb_header">
    <div class="pb_header_inner">
        <a href="/wherehouse" class="pb_logo" target="_top">WhereHouse</a>
        <h1 class="pb_title">매물 게시판</h1>
        <div class="pb_header_actions">
            <button id="btn_register" onclick="location.href='/wherehouse/properties/register'">매물 등록</button>
            <a href="/wherehouse" class="pb_home_link" target="_top">홈으로</a>
        </div>
    </div>
</header>

<!-- 필터 바 -->
<section id="pb_filter_bar">
    <div class="pb_filter_inner">
        <div class="pb_filter_group">
            <label>임대유형</label>
            <select id="filter_lease_type">
                <option value="">전체</option>
                <option value="CHARTER">전세</option>
                <option value="MONTHLY">월세</option>
            </select>
        </div>
        <div class="pb_filter_group">
            <label>지역구</label>
            <select id="filter_district">
                <option value="">전체</option>
                <option value="강남구">강남구</option><option value="강동구">강동구</option>
                <option value="강북구">강북구</option><option value="강서구">강서구</option>
                <option value="관악구">관악구</option><option value="광진구">광진구</option>
                <option value="구로구">구로구</option><option value="금천구">금천구</option>
                <option value="노원구">노원구</option><option value="도봉구">도봉구</option>
                <option value="동대문구">동대문구</option><option value="동작구">동작구</option>
                <option value="마포구">마포구</option><option value="서대문구">서대문구</option>
                <option value="서초구">서초구</option><option value="성동구">성동구</option>
                <option value="성북구">성북구</option><option value="송파구">송파구</option>
                <option value="양천구">양천구</option><option value="영등포구">영등포구</option>
                <option value="용산구">용산구</option><option value="은평구">은평구</option>
                <option value="종로구">종로구</option><option value="중구">중구</option>
                <option value="중랑구">중랑구</option>
            </select>
        </div>
        <div class="pb_filter_group">
            <label>상태</label>
            <select id="filter_status">
                <option value="ACTIVE">활성</option>
                <option value="COMPLETED">거래완료</option>
                <option value="">전체</option>
            </select>
        </div>
        <div class="pb_filter_group">
            <label>출처</label>
            <select id="filter_data_source">
                <option value="">전체</option>
                <option value="BATCH">배치(국토부)</option>
                <option value="USER">사용자 등록</option>
                <option value="MERGED">병합</option>
            </select>
        </div>
        <div class="pb_filter_group">
            <label>정렬</label>
            <select id="filter_sort">
                <option value="latest">최신순</option>
                <option value="priceDesc">가격 높은순</option>
                <option value="priceAsc">가격 낮은순</option>
                <option value="areaDesc">면적 넓은순</option>
                <option value="areaAsc">면적 좁은순</option>
            </select>
        </div>
        <div class="pb_filter_group pb_filter_search">
            <label>검색</label>
            <input type="text" id="filter_keyword" placeholder="아파트명 또는 지역구명">
        </div>
        <button id="btn_apply_filter" onclick="applyFilter()">검색</button>
    </div>
</section>

<!-- 결과 요약 -->
<section id="pb_result_summary">
    <span id="result_count">매물 0건</span>
    <span id="result_page_info"></span>
</section>

<!-- 매물 목록 -->
<main id="pb_property_list">
    <div id="property_cards_container">
        <!-- JS가 카드를 렌더링 -->
    </div>
</main>

<!-- 페이지네이션 -->
<nav id="pb_pagination">
</nav>

<!-- 상세 조회 모달 -->
<div id="detail_modal" class="pb_modal" style="display:none;">
    <div class="pb_modal_overlay" onclick="closeDetailModal()"></div>
    <div class="pb_modal_content pb_modal_large">
        <div class="pb_modal_header">
            <h3>매물 상세 정보</h3>
            <button class="pb_modal_close" onclick="closeDetailModal()">&times;</button>
        </div>
        <div class="pb_modal_body" id="detail_modal_body">
        </div>
    </div>
</div>

<!-- 수정 모달 -->
<div id="edit_modal" class="pb_modal" style="display:none;">
    <div class="pb_modal_overlay" onclick="closeEditModal()"></div>
    <div class="pb_modal_content">
        <div class="pb_modal_header">
            <h3>매물 수정</h3>
            <button class="pb_modal_close" onclick="closeEditModal()">&times;</button>
        </div>
        <div class="pb_modal_body">
            <input type="hidden" id="edit_property_id">
            <input type="hidden" id="edit_lease_type">

            <div class="pb_form_group">
                <label>전세금 / 보증금 (만원)</label>
                <input type="number" id="edit_deposit" min="1">
            </div>
            <div class="pb_form_group" id="edit_monthly_rent_group" style="display:none;">
                <label>월세금 (만원)</label>
                <input type="number" id="edit_monthly_rent" min="1">
            </div>
            <div class="pb_form_group">
                <label>건축연도</label>
                <input type="number" id="edit_build_year" min="1900" max="2030">
            </div>
            <div class="pb_form_group">
                <label>계약일자</label>
                <input type="date" id="edit_deal_date">
            </div>

            <div class="pb_form_actions">
                <button class="pb_btn pb_btn_cancel" onclick="closeEditModal()">취소</button>
                <button class="pb_btn pb_btn_save" onclick="submitEdit()">저장</button>
            </div>
        </div>
    </div>
</div>

<!-- 상태변경 확인 모달 -->
<div id="status_modal" class="pb_modal" style="display:none;">
    <div class="pb_modal_overlay" onclick="closeStatusModal()"></div>
    <div class="pb_modal_content pb_modal_small">
        <div class="pb_modal_header">
            <h3 id="status_modal_title">상태 변경</h3>
            <button class="pb_modal_close" onclick="closeStatusModal()">&times;</button>
        </div>
        <div class="pb_modal_body">
            <p id="status_modal_message"></p>
            <input type="hidden" id="status_property_id">
            <input type="hidden" id="status_lease_type">
            <input type="hidden" id="status_target">
            <div class="pb_form_actions">
                <button class="pb_btn pb_btn_cancel" onclick="closeStatusModal()">취소</button>
                <button class="pb_btn pb_btn_danger" onclick="submitStatusChange()">확인</button>
            </div>
        </div>
    </div>
</div>

<script src="/wherehouse/js/property_board.js"></script>
<!-- 방문 예약 슬롯 선택 모달(공통) — 매물 카드의 "방문 예약" 버튼이 호출 -->
<script src="/wherehouse/js/visit_slot_picker.js"></script>
</body>
</html>
