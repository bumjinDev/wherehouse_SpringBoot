<%@ page contentType="text/html; charset=UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script
            src="https://cdnjs.cloudflare.com/ajax/libs/jquery/3.6.0/jquery.min.js"
            integrity="sha512-894YE6QWD5I59HgZOGReFYm4dnWc1Qt5NtvYSaNcOP+u1T9qYdvdihz0PPSiiqn/+/3e7Jo4EaG7TubfWGUrMQ=="
            crossorigin="anonymous" referrerpolicy="no-referrer"></script>
    <!-- 카카오맵 API 키 세팅 -->
    <script type="text/javascript"
            src="//dapi.kakao.com/v2/maps/sdk.js?appkey=1583df647e490a6bc396830aa4c729ef&libraries=services,drawing"></script>
    <!-- 이모티콘 용 -->
    <script src="https://kit.fontawesome.com/eafa49c7a2.js" crossorigin="anonymous"></script>
    <script src="/wherehouse/js/LoadMapData.js"></script>

    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/information.css">
    <title>상세 페이지</title>
</head>
<body>
<div id="map" style="width:1832px; height:945px;"></div>
<div id="information">
    <div id="btn">◀</div>
    <div id="infoPanel"></div>
    <div id="detail">
        <ul class="address">
            <li id="addr">지도를 클릭 해 주세요.</li>
            <li class="detailAddr" style="margin-top: 12px;">도로명 : -<br>지번 : -</li>
        </ul>
        <ul class="detailInfo">
            <li id="cctv">
                <div>CCTV 수 :</div>
                <div id="cctvPcs" style="text-align: right;">- 개</div>
            </li>
            <li id="policeOffice">
                <div>가까운 파출소 :</div>
                <div id="distance" style="text-align: right;">- M</div>
            </li>
            <li id="amenity">
                <div style="margin: 0 auto; width: 90%; line-height: 150%; font-size: 1.3rem;">인근 편의시설</div>
                <i class="fa-regular fa-circle-question" id="question"></i>
                <div id="questionTip">·지하철역은 가장 우선 표기 됩니다.<br>
                    <br>·각 항목 클릭시 거리가 표시 됩니다.<br>
                    <br>·제공되는 편의시설의 정보 :
                    <br> &nbsp;&nbsp;&nbsp; 지하철역, 편의점, 음식점, 카페,
                    <br> &nbsp;&nbsp;&nbsp; 대형마트, 은행, 공공기관, 문화시설,
                    <br> &nbsp;&nbsp;&nbsp; 병원, 약국, 주차장, 주유소,
                    <br> &nbsp;&nbsp;&nbsp; 학교, 학원, 관광명소, 숙박 </div>
                <div class="each-menu" id="each1" style="color: #3a80e9;">-</div>
                <div class="each-menu" id="each2" style="color: #3a80e9;">-</div>
                <div class="each-menu" id="each3" style="color: #3a80e9;">-</div>
                <div class="each-menu" id="each4" style="color: #3a80e9;">-</div>
                <div class="each-menu" id="each5" style="color: #3a80e9;">-</div>
            </li>
            <li id="pinImgs">
                <img src="${pageContext.request.contextPath}/images/pin_icon.png" alt="">
                <img src="${pageContext.request.contextPath}/images/pin_icon.png" alt="">
                <img src="${pageContext.request.contextPath}/images/pin_icon.png" alt="">
                <img src="${pageContext.request.contextPath}/images/pin_icon.png" alt="">
                <img src="${pageContext.request.contextPath}/images/pin_icon.png" alt="">
            </li>
            <div class="tip" style="bottom: 0; right: 6px; bottom: 3px;">*반경 500m 범위의 정보 입니다.</div>
        </ul>
    </div>
    <div id="section">
        <div id="sectionName">종합 점수</div>
        <ul id="view">
            <li id="tag">
                <div style="margin-top: 6px;">종합</div>
                <div>안전성</div>
                <div>편의성</div>
            </li>
            <li id="graph">
                <div id="total" class="graphBar" style="margin-top: 7px;"></div>
                <div id="safty" class="graphBar"></div>
                <div id="conv" class="graphBar"></div>
            </li>
            <div id="infoDiv">
                <div id="infoDivDetail">이모티콘</div>
                <div id="infoDivScore">그래프 점수</div>
            </div>
        </ul>
        <!-- <div class="option">
            <div class="check_wrap">
            <a href="/wherehouse/page/reinfo#first_page" style="justify-content: left;">안전성 점수 산정 방식 <div>보러가기 =></div></a>
            <a href="/wherehouse/page/reinfo#second_page" style="margin-left: 48px;">편의성 점수 산정 방식 <div>보러가기 =></div></a>
            <input type="checkbox" id="check_btn" />
            </div>
            <div class="dropdown">선호 유형
                <select id="preference">
                    <option value="none" selected>-</option>
                    <option value="safty">안전</option>
                    <option value="conv">편의</option>
                </select>
            </div>
        </div> -->
        <ul id="link">
        </ul>
    </div>
    <footer>
        <img src="${pageContext.request.contextPath}/images/home_icon.png" alt="" style="width: 110px;">
        <div class="tip" style="font-size: 0.64rem;">마포구 신촌로 176 / 마포구 대흥동 12-20 / 전화번호 : 02-313-1711</div>
    </footer>
</div>

<!-- API 모듈 먼저 로드 -->
<script type="module" src="${pageContext.request.contextPath}/js/api.module.js"></script>

<!-- 기본 스크립트들 -->
<script src="${pageContext.request.contextPath}/js/map.js"></script>
<script src="${pageContext.request.contextPath}/js/panel.js"></script>
<script src="${pageContext.request.contextPath}/js/marker.js"></script>
<script src="${pageContext.request.contextPath}/js/circle.js"></script>
<script src="${pageContext.request.contextPath}/js/graph.js"></script>
<script src="${pageContext.request.contextPath}/js/policeOffice.js"></script>
<script src="${pageContext.request.contextPath}/js/cctv.js"></script>
<script src="${pageContext.request.contextPath}/js/amenity.js"></script>
<script type="module" src="${pageContext.request.contextPath}/js/mouseEvent.js"></script>
<script src="${pageContext.request.contextPath}/js/emoji.js"></script>
<script src="${pageContext.request.contextPath}/js/polygonView.js"></script>
</body>
</html>
