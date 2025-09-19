<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script type="text/javascript" src="https://dapi.kakao.com/v2/maps/sdk.js?appkey=1583df647e490a6bc396830aa4c729ef"></script>
    <script src="https://code.jquery.com/jquery-3.4.1.js"></script>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.2/js/bootstrap.min.js"></script>
    <script src="https://kit.fontawesome.com/09b067fdc5.js" crossorigin="anonymous"></script>

    <link rel="stylesheet" href="/wherehouse/css/house_rec.css">
</head>

<style>
    #map {
        width: 100%;
        height: 100vh;
        position: absolute;
        top: 0;
        left: 0;
        z-index: 1;
    }

    #information {
        position: relative;
        z-index: 10;
    }
</style>

<body>
<div id="map"></div>

<!-- 매물 상세 모달 창 수정 -->
<div id="property_detail_modal" style="display: none;">
    <div class="property_modal_overlay">
        <div class="property_modal_content">
            <div class="property_modal_header">
                <h3 id="property_modal_title">지역구 상세 매물 목록</h3>
                <button class="property_modal_close" onclick="closePropertyModal()">&times;</button>
            </div>
            <div class="property_modal_body">
                <div id="property_list_container">
                    <!-- JavaScript로 동적 생성될 매물 목록 -->
                </div>
            </div>
        </div>
    </div>
</div>

<!-- === 2차 명세: 상세 순위 정보 패널 모달 추가 === -->
<div id="detail_rank_modal" style="display: none;">
    <div class="detail_rank_modal_overlay">
        <div class="detail_rank_modal_content">
            <div class="detail_rank_modal_header">
                <h3 id="detail_rank_modal_title">지역구 상세 순위 정보</h3>
                <button class="detail_rank_modal_close" onclick="closeDetailRankModal()">&times;</button>
            </div>
            <div class="detail_rank_modal_body">
                <div id="detail_rank_container">
                    <!-- 지역구 기본 정보 -->
                    <div class="rank_info_section">
                        <h4 class="rank_section_title">기본 정보</h4>
                        <div class="rank_basic_info">
                            <div class="rank_info_item">
                                <span class="rank_info_label">지역구명:</span>
                                <span class="rank_info_value" id="rank_district_name">-</span>
                            </div>
                            <div class="rank_info_item">
                                <span class="rank_info_label">추천 순위:</span>
                                <span class="rank_info_value rank_highlight" id="rank_position">-</span>
                            </div>
                        </div>
                    </div>

                    <!-- 점수 정보 섹션 -->
                    <div class="rank_scores_section">
                        <h4 class="rank_section_title">상세 점수 정보</h4>

                        <!-- 매물 비용 점수 -->
                        <div class="score_item">
                            <div class="score_header">
                                <span class="score_label">매물 비용 점수</span>
                                <span class="score_value" id="average_price_score">0점</span>
                            </div>
                            <div class="score_bar_container">
                                <div class="score_bar">
                                    <div class="score_fill price_fill" id="price_score_bar"></div>
                                </div>
                            </div>
                            <div class="score_desc">해당 지역구 매물들의 가격 경쟁력 (낮을수록 좋음)</div>
                        </div>

                        <!-- 평수 점수 -->
                        <div class="score_item">
                            <div class="score_header">
                                <span class="score_label">평수 점수</span>
                                <span class="score_value" id="average_space_score">0점</span>
                            </div>
                            <div class="score_bar_container">
                                <div class="score_bar">
                                    <div class="score_fill space_fill" id="space_score_bar"></div>
                                </div>
                            </div>
                            <div class="score_desc">해당 지역구 매물들의 공간 만족도 (높을수록 좋음)</div>
                        </div>

                        <!-- 주거지 추천 점수 -->
                        <div class="score_item">
                            <div class="score_header">
                                <span class="score_label">주거지 추천 점수</span>
                                <span class="score_value" id="district_safety_score">0점</span>
                            </div>
                            <div class="score_bar_container">
                                <div class="score_bar">
                                    <div class="score_fill safety_fill" id="safety_score_bar"></div>
                                </div>
                            </div>
                            <div class="score_desc">해당 지역구의 안전성 및 주거 환경 (높을수록 좋음)</div>
                        </div>
                    </div>

                    <!-- 종합 평가 섹션 -->
                    <div class="rank_summary_section">
                        <h4 class="rank_section_title">종합 평가</h4>
                        <div class="rank_summary_content">
                            <p id="rank_summary_text">이 지역구의 추천 근거와 특징을 여기에 표시합니다.</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- 사용자 입력 패널 -->
<div id="information">
    <div id="btn">▼</div>
    <aside id="side-bar">
        <!-- 사용자 입력창 -->
        <div id="user-input">
            <div class="house_recommend">
                거주지 추천
            </div>

            <!-- 1단계: 기본 조건 설정 -->
            <div class="select_need">
                <p>1. 기본 조건 설정</p>
                <hr class="gu_name_hr" id="char_month_hr">

                <!-- 임대료 형태 선택 -->
                <div class="input_label">임대료 형태:</div>
                <div class="btn-group check_need" role="group" aria-label="Basic radio toggle button group">
                    <input type="radio" name="rentalType" class="btn-check" id="btn_charter" value="CHARTER" autocomplete="off" checked>
                    <label for="btn_charter" class="btn btn-outline-primary">전세</label>
                    <div style="width: 40px;"></div>
                    <input type="radio" name="rentalType" class="btn-check" id="btn_monthly" value="MONTHLY" autocomplete="off">
                    <label for="btn_monthly" class="btn btn-outline-primary">월세</label>
                </div>

                <!-- 예산 범위 입력 -->
                <div class="input_label">예산 범위:</div>
                <div class="range_input">
                    <input type="number" id="budgetMin" placeholder="2000" min="0">
                    <span class="range_separator">~</span>
                    <input type="number" id="budgetMax" placeholder="4500" min="0">
                    <span class="unit_text">만원</span>
                </div>
                <div class="error_message" id="budgetError"></div>

                <!-- 월세 추가 필드 -->
                <div class="monthly_extra_fields" id="monthlyExtraFields">
                    <div class="input_label">월세 범위:</div>
                    <div class="range_input">
                        <input type="number" id="monthlyMin" placeholder="30" min="0">
                        <span class="range_separator">~</span>
                        <input type="number" id="monthlyMax" placeholder="70" min="0">
                        <span class="unit_text">만원</span>
                    </div>
                </div>

                <!-- 평수 범위 입력 -->
                <div class="input_label">평수 범위:</div>
                <div class="range_input">
                    <input type="number" id="areaMin" placeholder="20.0" min="0" step="0.1">
                    <span class="range_separator">~</span>
                    <input type="number" id="areaMax" placeholder="30.0" min="0" step="0.1">
                    <span class="unit_text">평</span>
                </div>
                <div class="error_message" id="areaError"></div>
            </div>

            <!-- 2단계: 우선 순위 설정 -->
            <div class="priority_selection">
                <p>2. 우선 순위 설정</p>
                <hr class="gu_name_hr">

                <div class="priority_row">
                    <div class="priority_label">1순위:</div>
                    <select class="priority_select" id="priority1">
                        <option value="">선택해주세요</option>
                        <option value="PRICE">가격 (저렴한 매물 우선)</option>
                        <option value="SAFETY">안전성 (치안이 좋은 지역 우선)</option>
                        <option value="SPACE">공간 (넓은 평수 우선)</option>
                    </select>
                    <div class="priority_weight">60%</div>
                </div>

                <div class="priority_row">
                    <div class="priority_label">2순위:</div>
                    <select class="priority_select" id="priority2">
                        <option value="">선택해주세요</option>
                        <option value="PRICE">가격 (저렴한 매물 우선)</option>
                        <option value="SAFETY">안전성 (치안이 좋은 지역 우선)</option>
                        <option value="SPACE">공간 (넓은 평수 우선)</option>
                    </select>
                    <div class="priority_weight">30%</div>
                </div>

                <div class="priority_row">
                    <div class="priority_label">3순위:</div>
                    <select class="priority_select" id="priority3">
                        <option value="">선택해주세요</option>
                        <option value="PRICE">가격 (저렴한 매물 우선)</option>
                        <option value="SAFETY">안전성 (치안이 좋은 지역 우선)</option>
                        <option value="SPACE">공간 (넓은 평수 우선)</option>
                    </select>
                    <div class="priority_weight">10%</div>
                </div>

                <div class="priority_desc">각각 다른 우선순위를 선택해주세요</div>
                <div class="error_message" id="priorityError"></div>
            </div>

            <!-- 3단계: 유연성 설정 -->
            <div class="flexibility_settings">
                <p>3. 유연성 설정</p>
                <hr class="gu_name_hr">

                <div class="flex_item">
                    <div class="flex_label">
                        <span>예산 유연성</span>
                        <span class="flex_value" id="budgetFlexValue">10%</span>
                    </div>
                    <input type="range" class="flex_slider" id="budgetFlexibility" min="0" max="50" value="10">
                    <div class="flex_desc">매물이 부족할 때 최대 예산 초과 허용 범위</div>
                </div>

                <div class="flex_item">
                    <div class="flex_label">
                        <span>최소 안전 점수</span>
                        <span class="flex_value" id="safetyScoreValue">70점</span>
                    </div>
                    <input type="range" class="flex_slider" id="minSafetyScore" min="0" max="100" value="70">
                    <div class="flex_desc">안전성을 중시한다면 낮게 설정하세요</div>
                </div>

                <div class="flex_item">
                    <div class="flex_label">
                        <span>절대 최소 평수</span>
                    </div>
                    <div class="min_area_input">
                        <input type="number" id="absoluteMinArea" placeholder="15.0" min="0" step="0.1">
                        <span class="unit_text">평</span>
                    </div>
                    <div class="flex_desc">이 평수보다 작은 매물은 절대 추천받지 않습니다</div>
                </div>
            </div>

            <!-- 추천 결과 확인 버튼 -->
            <div id="recommend_result">
                <div id="recommend_result_btn">
                    <input type="button" value="추천 결과 확인" disabled>
                </div>
            </div>
        </div>

        <!-- 전세 추천 결과 페이지 -->
        <div id="charter_result_page" style="display:none;">
            <div class="house_recommend">
                전세 거주지 추천
            </div>
            <div id="charter_result_title">
                <p onclick="showInputPage()">▲</p> 전세 거주지 추천 결과
            </div>

            <!-- 상단 메시지 영역 - 기본 텍스트 제거 -->
            <div id="charter_recommendation_message">
                <p id="charter_message_text"></p>
            </div>

            <!-- 전세 추천 지역구 목록 -->
            <div id="charter_districts_container">
                <!-- JavaScript로 동적 생성될 전세 지역구 카드들 -->
            </div>
        </div>

        <!-- 월세 추천 결과 페이지 -->
        <div id="monthly_result_page" style="display:none;">
            <div class="house_recommend">
                월세 거주지 추천
            </div>
            <div id="monthly_result_title">
                <p onclick="showInputPage()">▲</p> 월세 거주지 추천 결과
            </div>

            <!-- 상단 메시지 영역 - 기본 텍스트 제거 -->
            <div id="monthly_recommendation_message">
                <p id="monthly_message_text"></p>
            </div>

            <!-- 월세 추천 지역구 목록 -->
            <div id="monthly_districts_container">
                <!-- JavaScript로 동적 생성될 월세 지역구 카드들 -->
            </div>
        </div>
    </aside>
</div>

<script src="/wherehouse/js/house_rec.js"></script>
</body>
</html>