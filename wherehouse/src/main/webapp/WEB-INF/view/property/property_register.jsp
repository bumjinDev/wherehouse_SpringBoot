<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wherehouse - 매물 등록</title>
    <link rel="stylesheet" href="/wherehouse/css/property_board.css">
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
        <h1 class="pb_title">매물 등록</h1>
        <div class="pb_header_actions">
            <button onclick="location.href='/wherehouse/properties/board'" class="pb_btn pb_btn_cancel">게시판으로</button>
            <a href="/wherehouse" class="pb_home_link" target="_top">홈으로</a>
        </div>
    </div>
</header>

<main id="register_container">

    <div id="register_auth_notice" style="display:none;">
        <p>매물 등록은 로그인이 필요합니다.</p>
        <button onclick="location.href='/wherehouse/members/login'" class="pb_btn pb_btn_save">로그인 하기</button>
    </div>

    <form id="register_form" class="pb_register_form" onsubmit="return false;">

        <!-- 임대유형 선택 -->
        <div class="pb_form_group">
            <label>임대유형 <span class="pb_required">*</span></label>
            <div class="pb_radio_group">
                <label class="pb_radio_label">
                    <input type="radio" name="lease_type" value="CHARTER" checked onchange="toggleMonthlyRent()"> 전세
                </label>
                <label class="pb_radio_label">
                    <input type="radio" name="lease_type" value="MONTHLY" onchange="toggleMonthlyRent()"> 월세
                </label>
            </div>
        </div>

        <div class="pb_form_section_title">불변 속성 (등록 후 수정 불가)</div>

        <div class="pb_form_row">
            <div class="pb_form_group">
                <label>아파트명 <span class="pb_required">*</span></label>
                <input type="text" id="reg_apt_nm" maxlength="100" placeholder="예: 래미안아파트" required>
            </div>
            <div class="pb_form_group">
                <label>지역구 <span class="pb_required">*</span></label>
                <select id="reg_sgg_cd" required>
                    <option value="">선택</option>
                    <option value="11110">종로구</option><option value="11140">중구</option>
                    <option value="11170">용산구</option><option value="11200">성동구</option>
                    <option value="11215">광진구</option><option value="11230">동대문구</option>
                    <option value="11260">중랑구</option><option value="11290">성북구</option>
                    <option value="11305">강북구</option><option value="11320">도봉구</option>
                    <option value="11350">노원구</option><option value="11380">은평구</option>
                    <option value="11410">서대문구</option><option value="11440">마포구</option>
                    <option value="11470">양천구</option><option value="11500">강서구</option>
                    <option value="11530">구로구</option><option value="11545">금천구</option>
                    <option value="11560">영등포구</option><option value="11590">동작구</option>
                    <option value="11620">관악구</option><option value="11650">서초구</option>
                    <option value="11680">강남구</option><option value="11710">송파구</option>
                    <option value="11740">강동구</option>
                </select>
            </div>
        </div>

        <div class="pb_form_row">
            <div class="pb_form_group">
                <label>법정동 <span class="pb_required">*</span></label>
                <input type="text" id="reg_umd_nm" maxlength="100" placeholder="예: 역삼동" required>
            </div>
            <div class="pb_form_group">
                <label>지번 <span class="pb_required">*</span></label>
                <input type="text" id="reg_jibun" maxlength="50" placeholder="예: 123-45" required>
            </div>
        </div>

        <div class="pb_form_row">
            <div class="pb_form_group">
                <label>층수 <span class="pb_required">*</span></label>
                <input type="number" id="reg_floor" min="-10" max="100" placeholder="예: 12" required>
            </div>
            <div class="pb_form_group">
                <label>전용면적 (㎡) <span class="pb_required">*</span></label>
                <input type="number" id="reg_exclu_use_ar" min="0.0001" step="0.0001" placeholder="예: 59.84" required>
            </div>
        </div>

        <div class="pb_form_section_title">가변 속성</div>

        <div class="pb_form_row">
            <div class="pb_form_group">
                <label id="deposit_label">전세금 (만원) <span class="pb_required">*</span></label>
                <input type="number" id="reg_deposit" min="1" placeholder="예: 45000" required>
            </div>
            <div class="pb_form_group" id="reg_monthly_rent_group" style="display:none;">
                <label>월세금 (만원) <span class="pb_required">*</span></label>
                <input type="number" id="reg_monthly_rent" min="1" placeholder="예: 80">
            </div>
        </div>

        <div class="pb_form_row">
            <div class="pb_form_group">
                <label>건축연도</label>
                <input type="number" id="reg_build_year" min="1900" max="2030" placeholder="예: 2015">
            </div>
            <div class="pb_form_group">
                <label>계약일자</label>
                <input type="date" id="reg_deal_date">
            </div>
        </div>

        <div id="register_error" class="pb_error_msg" style="display:none;"></div>

        <div class="pb_form_actions pb_form_actions_center">
            <button type="button" class="pb_btn pb_btn_cancel" onclick="location.href='/wherehouse/properties/board'">취소</button>
            <button type="button" class="pb_btn pb_btn_save" onclick="submitRegister()">등록</button>
        </div>

    </form>
</main>

<script src="/wherehouse/js/property_register.js"></script>
</body>
</html>
