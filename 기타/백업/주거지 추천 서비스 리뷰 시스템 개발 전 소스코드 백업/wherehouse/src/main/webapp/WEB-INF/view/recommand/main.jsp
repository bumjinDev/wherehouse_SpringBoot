<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>WhereHouse</title>
    <link rel="stylesheet" href="/wherehouse/css/main.css?ver=123">
    <link rel="icon" href="/wherehouse/images/home_icon.png">  <!--  <link rel="icon" href="/wherehouse/images/home_icon.png"> -->
    <script src="/wherehouse/js/main.js"></script>
</head>
<body>
    <nav>
        <div id="menu">
            <div id="menu_home">
                <div id="menu_home_icon">
                    <a href="/wherehouse" ><img src="/wherehouse/images/home_icon.png" alt=""></a>		<!-- 별도 스프링 컨트롤러 거치치 않고 바로 컨텍스트 요청 실행. -->
                </div>
                <hr id="menu_division">
            </div>

            <div id="menu_suggest">
                <div id="menu_suggest_icon">
                    <img src="/wherehouse/images/suggest_icon.png" alt="">
                </div>
                <div class="menu_txt">
                    거주지 추천
                </div>
            </div>
            <div id="menu_gu">
                <div id="menu_gu_icon">
                    <img src="/wherehouse/images/gu_icon.png" alt="">
                </div>
                <div class="menu_txt">
                    지역구 지도
                </div>
            </div>
            <div id="menu_detail">
                <div id="menu_detail_icon">
                    <img src="/wherehouse/images/detail_icon.png" alt="">
                </div>
                <div class="menu_txt">
                    상세 지도
                </div>
            </div>
            <div id="menu_board">
                <div id="menu_board_icon">
                    <img src="/wherehouse/images/board_icon.png" alt="">
                </div>
                <div class="menu_txt">
                    게시판
                </div>
            </div>
        </div>
    </nav>
    <section>
        <iframe src="" frameborder="0" id="iframe_section"></iframe>
    </section>
</body>
</html>