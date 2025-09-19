<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="./css/bootstrap.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.2/js/bootstrap.min.js"></script>
   	<!-- <script src="/wherehouse/json/mapData.json"></script> -->
   	<!-- <script src="/wherehouse/js/LoadMapData.js"></script>  -->
    <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=1583df647e490a6bc396830aa4c729ef">
    </script>
    <script src="https://kit.fontawesome.com/09b067fdc5.js" crossorigin="anonymous"></script>
    <link rel="stylesheet" href="./css/gu_map.css">
    <link rel="stylesheet" href="./css/gu_main_map.css">
    <link rel="stylesheet" href="./css/gu_map_graph.css">
    <link rel="stylesheet" href="./css/gu_map_info.css">

</head>
<body>
    <div id="map">
    </div>

    <div id="information">
        <div id="btn"><span>◀</span></div>

        <aside id="side-bar">
            <div class="gu-info">
                지역구 정보
            </div>
            <div class="gu-choose">
                <select name="" id="gu_select">
                    <option value="default">지역구를 선택해주세요</option>
                    <option value="강남구">강남구</option>
                    <option value="강동구">강동구</option>
                    <option value="강북구">강북구</option>
                    <option value="강서구">강서구</option>
                    <option value="관악구">관악구</option>
                    <option value="광진구">광진구</option>
                    <option value="구로구">구로구</option>
                    <option value="금천구">금천구</option>
                    <option value="노원구">노원구</option>
                    <option value="도봉구">도봉구</option>
                    <option value="동대문구">동대문구</option>
                    <option value="동작구">동작구</option>
                    <option value="마포구">마포구</option>
                    <option value="서대문구">서대문구</option>
                    <option value="서초구">서초구</option>
                    <option value="성동구">성동구</option>
                    <option value="성북구">성북구</option>
                    <option value="송파구">송파구</option>
                    <option value="양천구">양천구</option>
                    <option value="영등포구">영등포구</option>
                    <option value="용산구">용산구</option>
                    <option value="은평구">은평구</option>
                    <option value="종로구">종로구</option>
                    <option value="중구">중구</option>
                    <option value="중랑구">중랑구</option>
                </select>
            </div>
            <div id="select_need">
                <p>평균 전세 / 월세 가격</p>
                <hr class="gu_name_hr">
                <div class="btn-group check_need" role="group" aria-label="Basic radio toggle button group">
                    <input type="radio" name="rentalType" class="btn-check" id="btn_charter" autocomplete="off">
                    <label for="btn_charter" class="btn btn-outline-primary">전세</label>
                    <div style="width: 40px;"></div>
                    <input type="radio" name="rentalType" class="btn-check" id="btn_monthly" autocomplete="off" checked>
                    <label for="btn_monthly" class="btn btn-outline-primary">월세</label>
                </div>
                <div id="average-charter">
                    <div id="charter-deposit">
                        전세금 : <span id="charter-deposit-fee">ex</span> 만원
                    </div>
                </div>
                <div id="average-monthly">
                    <div id="monthly-deposit">
                        보증금 : <span id="monthly-deposit-fee">ex</span> 만원
                    </div>
                    <div id="monthly-month">
                        월세금 : <span id="monthly-month-fee">ex</span> 만원
                    </div>
                </div>
            </div>


            <div id="average-score">
                <div id="total_title">
                    <span style="font-size: 2rem;">종합 점수</span>
                    <hr class="gu_name_hr">
                </div>
                <div id="average-score-inner">
                    <div id="average-score-safety">
                        <div id="safety_barChart" class="barChart">
                            <div id="safety_value">0</div>
                        </div>
                    </div>
                    <div id="average-score-convenience">
                        <div id="convenience_barChart" class="barChart">
                            <div id="convenience_value">0</div>
                        </div>
                    </div>
                </div>
                <div id="average-score-inner-text">
                    <div id="name_safety">생활 안전</div>
                    <div id="name_convenience">생활 편의</div>
                </div>
            </div>

            <div id="hotPlace_wrap">
                <div id="hotPlace_title">
                    <span>핫플레이스</span>
                    <hr class="gu_name_hr">
                </div>
                <!-- == Bootstrap 캐러셀 -->
                <div id="carousel-hotplace" class="carousel slide col-xs-1 col-xs-offset-1" data-ride="carousel"
                    data-interval="3000">		<!-- class="carousel slid" : .slide, 부드럽게 전환. -->
                    <div class="carousel-inner">		<!-- 캐러셀 아이템을 담는 컨테이너 정의, -->
                        <div class="item active">		<!-- 캐러셀 item을 정의, item active는 캐러셀 아이템 중 맨처음 보여질 이미지 -->
                            <img id="carousel-img1" src="" alt="메인이미지" srcset="" class="col-xs-12">
                            <div class="carousel-caption ms-5" id="carousel-caption1">
                            </div>
                        </div>
                        <div class="item">
                            <img id="carousel-img2" src="" alt="메인이미지" srcset="" class="col-xs-12">
                            <div class="carousel-caption" id="carousel-caption2">
                            </div>
                        </div>
                        <div class="item">
                            <img id="carousel-img3" src="" alt="메인이미지" srcset="" class="col-xs-12">
                            <div class="carousel-caption" id="carousel-caption3">
                            </div>
                        </div>
                    </div>
                </div>
                <!-- 캐러셀 == -->
            </div>
            <div id="logo-img"><img src="./images/home_icon.png" alt=""></div>
        </aside>
    </div>

    <div id="population-shame-bar">
        <div id="population-shame-text">인구 밀집도 수치</div>
        <div id="population-shame-btn">-</div>
    </div>
    <div id="population-shame-info">
        <div id="population-shame-num">
            <div id="population-shame-num-0">0</div>
            <div id="population-shame-num-7">7</div>
        </div>
        <div id="population-shame-graph">
            <div id="population-shame-graph-1"></div>
            <div id="population-shame-graph-2"></div>
            <div id="population-shame-graph-3"></div>
            <div id="population-shame-graph-4"></div>
            <div id="population-shame-graph-5"></div>
            <div id="population-shame-graph-6"></div>
            <div id="population-shame-graph-7"></div>
        </div>
    </div>

    <script src="./js/gu_map.js"></script>
</body>
</html>