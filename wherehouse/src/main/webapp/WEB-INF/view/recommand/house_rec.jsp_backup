<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=1583df647e490a6bc396830aa4c729ef&libraries=services"></script>
    <script src="https://code.jquery.com/jquery-3.4.1.js"></script>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.2/js/bootstrap.min.js"></script>
  	<!--  <script src="/wherehouse/json/mapData.json"></script> -->
  	<!-- <script src="/wherehouse/js/LoadMapData.js"></script> -->
    <script src="https://kit.fontawesome.com/09b067fdc5.js" crossorigin="anonymous"></script>
    <link rel="stylesheet" href="/wherehouse/css/house_rec.css">
    <link rel="stylesheet" href="/wherehouse/css/rec_graph.css?ver=123">
    <link rel="stylesheet" href="/wherehouse/css/gu_main_map.css">
    <link rel="stylesheet" href="/wherehouse/css/rec_info.css">
    <link rel="stylesheet" href="/wherehouse/css/comp_modal.css">
    
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div id="map">
    </div>

    <!-- 상세 비교 모달 창 -->
    <!-- 1. 버튼 "상세 비교"(<input type="button" id="compBtn">) 클릭 시 발생하는 모달 화면
    	 2. 사용자가 전/월세 선택과 금액 및 안정성/편의성 비중 선택 따른 3개 구에 대한 상세 비교 모달 페이지
    -->
    <div class="modal">
        <div class="modal-dialog">
            <div class="modal-header">
                <div class="modal-title">상세 비교
                    <button type="button" class="btn-close" id="modalCloseBtn">x</button>
                </div>
            </div>
            <div class="modal-body">
                <div class="modal-content">
                    <div class="modal-select">
                        <button type="button" id="btn_safety">생활 안전</button>
                        <button type="button" id="btn_convenience">생활 편의</button>
                    </div>
                    <div class="modal-select-content" id="modal-select-safety">
                        <button type="button" id="pollice_content_btn">파출소</button>
                        <button type="button" id="cctv_content_btn">CCTV</button>
                        <button type="button" id="arrest_content_btn">검거율</button>
                    </div>
                    <div class="modal-select-content" id="modal-select-conv">
                        <button type="button" id="convStore_content_btn">편의점</button>
                        <button type="button" id="restaurant_content_btn">음식점</button>
                        <button type="button" id="cafe_content_btn">카페</button>
                        <button type="button" id="olive_content_btn">올리브영</button>
                        <button type="button" id="daiso_content_btn">다이소</button>
                    </div>
                    <div id="modal-graph-wrap">
                        <div id="content_graph_container" class="container-graph">

                            <div id="content_graph_title" class="bar_title">편의점 수</div>
                            <div class="container_content_graph">
                                <div class="content_graph_wrap first_wrap" id="content_graph_gu1" >
                                    <div class="content_graph">
                                        <div id="content_bar1" class="content_bar graph_bar">
                                            <div id="content_value1" class="graph_value" ></div>
                                        </div>
                                    </div>
                                    <div class="label_gu1"></div>
                                </div>
                                <div class="content_graph_wrap second_wrap" id="content_graph_gu2">
                                    <div class="content_graph" >
                                        <div id="content_bar2" class="content_bar graph_bar">
                                            <div id="content_value2" class="graph_value"></div>
                                        </div>
                                    </div>
                                    <div class="label_gu2"></div>
                                </div>
                                <div class="content_graph_wrap third_wrap" id="content_graph_gu3">
                                    <div class="content_graph">
                                        <div id="content_bar3" class="content_bar graph_bar">
                                            <div id="content_value3" class="graph_value"></div>
                                        </div>
                                    </div>
                                    <div class="label_gu3"></div>
                                </div>
                            </div>

                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    </div>
    <!-- 상세 비교 모달 창 끝 -->

	<!-- 거주지 추천 정보 제공을 위한 사용자 정보 입력 화면, house_rec.js 파일에 따라 선택적으로 보여준다.
		  ** 사용자가 월세/전세 금액 정상적으로 입력 후 버튼 "추천 결과 확인" 선택 시 추천 지역 구 3개 보여주는 메소드 : "resultCheck()" 실행 및 "resultCheck()" 실행 중 내부 메소드 "showResult()" 내에서 ajax로 요청.   -->
    <div id="information">
        <div id="btn">◀</div>
        <aside id="side-bar">
            <!-- 사용자 입력창 -->
            <div id="user-input">
                <div class="house_recommend">
                    거주지 추천
                </div>
                <div class="select_need">
                    <p>전세 / 월세</p>
                    <hr class="gu_name_hr" id="char_month_hr">
                    <div class="btn-group check_need" role="group" aria-label="Basic radio toggle button group">
                        <input type="radio" name="rentalType" class="btn-check" id="btn_charter" autocomplete="off">
                        <label for="btn_charter" class="btn btn-outline-primary">전세</label>
                        <div style="width: 40px;"></div>
                        <input type="radio" name="rentalType" class="btn-check" id="btn_monthly" autocomplete="off" checked>
                        <label for="btn_monthly" class="btn btn-outline-primary">월세</label>
                    </div>
                    <div id="rentInput">
                        <!-- 전세를 선택한 경우 -->
                        <div id="charterInput">
                            <div> 전세금(최대):
                                <input type="text" name="charterDeposit" placeholder="15000 ~ 30000" class="inputPrice"> 만원
                            </div>
                        </div>

                        <!-- 월세를 선택한 경우 -->
                        <div id="monthlyInput">
                            <div>보증금(최대):
                                <input type="text" name="monthlyDeposit" placeholder="2100 ~ 4500" class="inputPrice"> 만원
                            </div>
                            <div>월세금(최대):
                                <input type="text" name="monthlyMonth" placeholder="40 ~ 70" class="inputPrice"> 만원
                            </div>
                        </div>
                    </div>
                </div>

                <!-- 치안 편의 선택창 -->
                <div class="safety_convenience">
                    <p style="margin: 0;"> 생활 안전 / 편의</p>
                    <hr class="gu_name_hr">
                    <div class="slider"> 안전
                        <input type="range" min="0" max="9" value="0" id="myRange_safety"> <span id="safety_f"> 0 단계</span>
                    </div>
                    <div class="slider">  편의
                        <input type="range" min="0" max="9" value="0" id="myRange_convenience">
                        	<span id="convenience_f"> 0 단계</span>
                    </div>
                    <div id="descript">
                        <div id="descript_safety"></div>
                        <div id="descript_convenience"></div>
                    </div>
                </div>

                <!-- 추천 결과 확인 -->
                <div id="recommend_result">
                    <div id="recommend_result_btn" onclick="resultCheck()">
                        <input type="button" disabled> 추천 결과 확인
                    </div>
                </div>
                
                            <div>&nbsp;</div>
            <div style="margin-left: 35px;">
                <a style="color: rgb(150, 150, 150);">*거주지 추천은 10평 이하 기준입니다.</a>
            </div>
                
            </div>
            <div id="recommend_result_page">
                <div class="house_recommend">
                    거주지 추천
                </div>
                <div id="recommend_result_title">
                    <p onclick="showRecommend()">◀</p> 거주지 추천 결과
                </div>
                
                <!-- 거주지 추천 위한 사용자 입력 정보(전/월세, 금액)를 토대로 각 3개 구에 대한 상세 비교 시 3개 구를 보여주며 각
                	 1번, 2번, 3번으로 표현하는 HTML 코드.
                	   1. 접었을 때는 각 번호와 지역 구 이름만 표현.
                	   2. 펼쳤을 때는 순서대로 금액, 생활 편의 점수, 안정성 점수 표현
                	 1번과 2번 항목에 대해 별도의 HTML 태그 작성.
                -->
                
                <!-- 3개 지역 구 비교 정보 중 첫번 째 지역구 -->
                <div id="recommend_first">
                    <div class="recommend_inner_text">
                        <div class="rec_result_div">
                            1. <span id="recommend_first_result">-</span>
                        </div>
                        <input type="checkbox" name="first" value="first" id="check_first">
                        <span id="recommend_first_btn" onclick="showDetailFirst()">▼</span>
                    </div>
                </div>
                
                <div id="recommend_first_info">
                    <div class="recommend_inner_text">
                        <div class="rec_result_div">
                            1. <span id="recommend_first_result_detail">-</span>
                        </div>
                        <input type="checkbox" name="first" value="first" id="check_first_info">
                        <span id="recommend_first_btn" onclick="hideDetailFirst()">▲</span>
                    </div>
                    <hr class="gu_name_hr">
                    
                      <!-- 전세 일때 표현되는 부분 -->
                    <div id="select_first_charter" class="select_price">
                        평균 전세금 : <span id="first_charter_fee" class="average_money">-</span> 만원
                    </div>
                    
                      <!-- 월세 일때 표현되는 부분 -->
                    <div id="select_first_monthly" class="select_price">
                        평균 보증금 : <span id="first_deposit_fee" class="average_money">-</span> 만원<br>
                        평균 월세금 : <span id="first_monthly_fee" class="average_money">-</span> 만원
                    </div>
                    <hr class="price_hr">
                    
                    <!-- 생활 안전 및 생활 편의 점수를 숫자와 함께 div 태그를 사용하여 그래픽으로 표현  -->
                    <div class="result_graph_wrap">
                        <div class="safety_wrap">
                            <div class="result_label_safety">생활 안전</div>
                            <div id="safety_first_graph" class="result_safety_graph">
                            </div>
                            <span id="safety_first_value">0</span>
                        </div>
                        <div class="conv_wrap">
                            <div class="result_label_conv">생활 편의</div>
                            <div id="conv_first_graph" class="result_conv_graph">
                            </div><span id="conv_first_value">0</span>
                        </div>
                    </div>
                </div>

				<!-- 3개 지역 구 비교 정보 중 두번 째 지역구 -->
                <div id="recommend_second">
                    <div class="recommend_inner_text">
                        <div class="rec_result_div">
                            2. <span id="recommend_second_result">-</span>
                        </div>
                        <input type="checkbox" name="second" value="second" id="check_second">
                        <span id="recommend_second_btn" onclick="showDetailSecond()">▼</span>
                    </div>
                </div>
                <div id="recommend_second_info">
                    <div class="recommend_inner_text">
                        <div class="rec_result_div">
                            2. <span id="recommend_second_result_detail">-</span>
                        </div>
                        <input type="checkbox" name="second" value="second" id="check_second_info">
                        <span id="recommend_second_btn" onclick="hideDetailSecond()">▲</span>
                    </div>
                    <hr class="gu_name_hr">
                    
                    <!-- 전세 일때 표현되는 부분 -->
                    <div id="select_second_charter" class="select_price">
                        평균 전세금 : <span id="second_charter_fee" class="average_money">-</span> 만원
                    </div>
                    
                     <!-- 월세 일때 표현되는 부분 -->
                    <div id="select_second_monthly" class="select_price">
                        평균 보증금 : <span id="second_deposit_fee" class="average_money">-</span> 만원<br>
                        평균 월세금 : <span id="second_monthly_fee" class="average_money">-</span> 만원
                    </div>
                    <hr class="price_hr">
                    
                   	<!-- 생활 안전 및 생활 편의 점수 표현  -->
                    <div class="result_graph_wrap">
                        <div class="safety_wrap">
                            <div class="result_label_safety">생활 안전</div>
                            <div id="safety_second_graph" class="result_safety_graph">
                            </div>
                            <span id="safety_second_value">0</span>
                        </div>
                        <div class="conv_wrap">
                            <div class="result_label_conv">생활 편의</div>
                            <div id="conv_second_graph" class="result_conv_graph">
                            </div><span id="conv_second_value">0</span>
                        </div>
                    </div>
                </div>

				<!-- 3개 지역 구 비교 정보 중 세번 째 지역구 -->
                <div id="recommend_third">
                    <div class="recommend_inner_text">
                        <div class="rec_result_div">
                            3. <span id="recommend_third_result">-</span>
                        </div>
                        <input type="checkbox" name="third" value="third" id="check_third">
                        <span id="recommend_third_btn" onclick="showDetailThird()">▼</span>
                    </div>
                </div>
                <div id="recommend_third_info">
                    <div class="recommend_inner_text">
                        <div class="rec_result_div">
                            3. <span id="recommend_third_result_detail">-</span>
                        </div>
                        <input type="checkbox" name="third" value="third" id="check_third_info">
                        <span id="recommend_third_btn" onclick="hideDetailThird()">▲</span>
                    </div>
                    <hr class="gu_name_hr">
                    
                      <!-- 전세 일때 표현되는 부분 -->
                    <div id="select_third_charter" class="select_price">
                        평균 전세금 : <span id="third_charter_fee" class="average_money">-</span> 만원
                    </div>
                    
                      <!-- 월세 일때 표현되는 부분 -->
                    <div id="select_third_monthly" class="select_price">
                        평균 보증금 : <span id="third_deposit_fee" class="average_money">-</span> 만원<br>
                        평균 월세금 : <span id="third_monthly_fee" class="average_money">-</span> 만원
                    </div>
                    <hr class="price_hr">
                    
                    <!-- 생활 안전 및 생활 편의 점수 표현  -->
                    <div class="result_graph_wrap">
                        <div class="safety_wrap">
                            <div class="result_label_safety">생활 안전</div>
                            <div id="safety_third_graph" class="result_safety_graph">
                            </div>
                            <span id="safety_third_value">0</span>
                        </div>
                        <div class="conv_wrap">
                            <div class="result_label_conv">생활 편의</div>
                            <div id="conv_third_graph" class="result_conv_graph">
                            </div><span id="conv_third_value">0</span>
                        </div>
                    </div>
                </div>

                <div id="recommend_comparison">	 <!-- 추천 받은 3개 구 중 1개 이상 선택 하여 상세 비교 모달 창 확인. -->
                    <div id="recommend_comparison_btn" onclick="showComparison()">
                        <input type="button" id="compBtn">상세 비교
                    </div>
                </div>
            </div>
        </aside>
    </div>    
    
    <!-- 선택된 지역구 왜 추천하는지에 대한 정보 보여지는 section --> 
    <!-- 거주지 추천 결과 확인 창 옆 위차한 section, 3개 구 중 1등 구에 대한 결과를 표현.
    		1. 안전 정보 : 현재 구가 전체 구와 비교해서 몇 번째로 안전한지 알려줌과 동시에 아래 정보 표현하며
    			구 평균의 파출소 수, CCTV 개수, 검거율을 현재 구와 비교 
    		2. 편의 정보 : 현재 구가 전체 구와 비교해서 몇 번째의 편의시설 정도를 비교하여 보여줌과 동시에
    			구 평균의 음식점, 편의점, 카페 수를 비교.
    -->
    <section id="chart_information">
        <!-- 선택된 지역구 왜 추천하는지에 대한 정보 -->
        <aside id="side-bar-second">
            <div class="house_recommend">
                <span class="chart_name" style="color: white;">동대문구</span>		<!-- section 중 첫번째 부분으로 지역 구 이름 -->
            </div>
            <div class="select-gu-info">
            
            	<!-- 안전정보/편의정보를 선택 위한 버튼 포함 항목, 각 항목 내 각 요소 별 전체 구 평균 수와  현재 지역 추천 1등 구를 숫자 및 그래프로 비교한다. -->
                <div class="btn-group check_chart_info" role="group" aria-label="Basic radio toggle button group">
                    <input type="radio" name="chartType" class="btn-check" id="btn_safe" autocomplete="off" checked>
                    <label for="btn_safe" class="btn btn-outline-primary">안전 정보</label>
                    <div style="width: 40px;"></div>
                    <input type="radio" name="chartType" class="btn-check" id="btn_conv" autocomplete="off">
                    <label for="btn_conv" class="btn btn-outline-primary">편의 정보</label>
                </div>
                <hr id="chart-start-hr">
                
                <!-- 안전정보 선택 시 보여지는 화면 -->
                <div id="safe-info">
                    <span class="chart_name">동대문구</span>의 안전 순위는 25개 구 중<br>
                    <span id="chart_safe_rank" class="chart_rank">1</span>번 째입니다.<br>
                    <hr id="rank-hr">
                    
                    <!-- chart.js 적용. -->          
                    <div class="chart-info">
                        <canvas id="policeOfficeChart"></canvas>		<!-- 태그 "canvas" 사용하여 chartjs 적용 -->
                    </div>
                    <hr class="chart-hr">
                    
                    
                    <div class="chart-info">
                        <canvas id="cctvChart"></canvas>
                    </div>
                    <hr class="chart-hr">

                    <div class="chart-info">
                        <canvas id="arrestChart"></canvas>
                    </div>
                    <hr class="chart-hr">
                </div>
                
                 <!-- 편의점수 선택 시 보여지는 화면 -->
                <div id="conv-info">
                    <span class="chart_name">동대문구</span>의 편의 순위는 25개 구 중<br>
                    <span id="chart_conv_rank" class="chart_rank">2</span>번 째입니다.<br>
                    <hr id="rank-hr">
                    
                    <!-- 실제 차트 보여지는 부분 -->                       
                    <div class="chart-info">
                        <canvas id="restaurantChart"></canvas>
                    </div>
                    <hr class="chart-hr">
                
                    
                    <div class="chart-info">
                        <canvas id="convStoreChart"></canvas>
                    </div>
                    <hr class="chart-hr">

                    <div class="chart-info">
                        <canvas id="cafeChart"></canvas>
                    </div>
                    <hr class="chart-hr">
                </div>
                <h3 style="color: #0B5ED7;"><<span class="chart_name" style="font-size:1.2rem;">동대문구</span>의 총평></h3>
					<span id="gu_review">강남3구답게 가격은 높은편 ! 하지만 <br>
					인구밀집도도 낮고 안전 편의 점수도 높아 <br>
					살기 좋은 지역이다</span>
            </div>
        </aside>
    </section>
    <!-- 선택된 지역구 왜 추천하는지에 대한 정보 보여지는 section 종료 --> 
    
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
    
    <script src="/wherehouse/js/house_rec.js"></script>
</body>
</html>