<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="icon" href="/wherehouse/images/home_icon.png">
    <link rel="stylesheet" href="/wherehouse/css/index.css">
    <link rel="stylesheet" href="/wherehouse/css/description.css">
    <script src="/wherehouse/js/description.js"></script>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Courgette&display=swap" rel="stylesheet">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Black+Han+Sans&display=swap" rel="stylesheet">

    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/css/bootstrap.min.css" rel="stylesheet"
        integrity="sha384-4bw+/aepP/YC94hEpVNVgiZdgIC5+VKNBQNGCHeKRQN+PtmoHDEXuppvnDJzQIu9" crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/js/bootstrap.bundle.min.js"
        integrity="sha384-HwwvtgBNo3bZJJLYd8oVXjrBZt8cqVSpeBNS5n7C8IVInixGAoxmnlMuBnhbgrkm"
        crossorigin="anonymous"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.7.1/font/bootstrap-icons.css">
    <title>WhereHouse</title>
</head>

<body>
    <!-- 네비 바 -->
    <header>
        <nav class="navbar navbar-expand-lg fixed-top bg-light border-bottom">
            <div class="container-fluid">
                <a id="logo" class="navbar-brand ms-4 py-3" href="/wherehouse/loginSuccess">Where House</a>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarScroll"
                    aria-controls="navbarScroll" aria-expanded="false" aria-label="Toggle navigation">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse justify-content-end me-4" id="navbarScroll">
                    <ul class="navbar-nav navbar-nav-scroll" style="--bs-scroll-height: 300px;">
                        <li class="nav-item dropdown">
                            <a class=" nav-link dropdown-toggle me-5" id="nav_btn_house_rec" href="#" role="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                                거주지 추천
                            </a>
                            <ul class="nav_btn_house_rec_dropdown dropdown-menu me-5 dropdown-menu-end text-end" id="">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/main">직접 입력 추천</a></li>
                            </ul>
                        </li>
                        <li class="nav-item dropdown">
                            <a class="nav-link dropdown-toggle me-5" id="nav_btn_gu_map" href="#" role="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                                지도
                            </a>
                            <ul class="dropdown-menu dropdown-menu-end me-4 text-end" id="nav_btn_gu_map_dropdown">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/main" id="gu_btn">지역구 지도</a></li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/main" id="detail_btn">상세 지도</a>
                                </li>
                            </ul>
                        </li>
                        <li class="nav-item dropdown">
                            <a class=" nav-link dropdown-toggle me-3" id="nav_btn_data_script" href="/wherehouse/reinfo" role="button"
                                data-bs-toggle="dropdown" aria-expanded="false">
                                데이터 분석
                            </a>
                            <ul class="nav_btn_house_rec_dropdown dropdown-menu me-5 dropdown-menu-end text-end" id="">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo">분석 적용 방식 페이지</a></li>
                                <hr id="navbar_hr">
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#first_page">생활 안전
                                        관련</a>
                                </li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#second_page">생활 편의
                                        관련</a>
                                </li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#third_page">가격 관련</a>
                                </li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#fourth_page">추천 결과
                                        관련</a>
                                </li>
                                <li><a class="dropdown-item nav_choose" href="/wherehouse/reinfo#fifth_page">인구 밀집도
                                        관련</a>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </div>
            </div>
        </nav>
    </header>
    <aside>
        <nav class="position-fixed top-50 end-0 pt-3 pb-3" id="sidenav">
            <div class="container">
                <ul class="navbar-nav">
                    <li class="nav-item">
                        <a class="nav-link" href="#top_text">TOP</a>
                        <hr>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#first_page">생활 안전</a>
                        <hr>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#second_page">생활 편의</a>
                        <hr>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#third_page">평균 가격</a>
                        <hr>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#fourth_page">추천 결과</a>
                        <hr>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" href="#fifth_page">인구 밀집도</a>
                    </li>
                </ul>
            </div>
        </nav>
    </aside>
    <!-- 설명 구간 -->
    <section class="bg-dark">
        <div class="mt-5 mb-5 pt-5 pb-5"></div>
        <div class="mt-5 mb-5 pt-5 pb-5"></div>

        <!-- 제목 -->
        <div class="mt-5 pt-5">
            <p class="text-center first_background" id="first_text">분석 결과 적용 방식</p>
        </div>
        <div class="mt-5 mb-5 pt-5 pb-5"></div>
        <div class="mt-5 mb-5 pt-5 pb-5"></div>
        <div class="mt-5 mb-5 pt-5 pb-5"></div>
        <div class="mt-5 mb-5 pt-5 pb-5"></div>

        <div class="container-fluid bg_control">
            <!-- 생활 안전 관련 점수&단계 산정 방식 -->
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>

            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5" id="top_text"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <h2 class="pt-5 pb-5" id="menu_text">결과 적용 카테고리</h2>
            <div class="container">
                <div class="row row-cols-1 row-cols-md-5 g-4">
                    <div class="col">
                        <div class="card">
                            <div class="card-body">
                                <h5 class="card-title"><a href="#first_page">생활 안전 관련</a></h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card">
                            <div class="card-body">
                                <h5 class="card-title"><a href="#second_page">생활 편의 관련</a></h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card">
                            <div class="card-body">
                                <h5 class="card-title"><a href="#third_page">가격 관련</a></h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card">
                            <div class="card-body">
                                <h5 class="card-title"><a href="#fourth_page">추천 결과 관련</a></h5>
                            </div>
                        </div>
                    </div>
                    <div class="col">
                        <div class="card">
                            <div class="card-body">
                                <h5 class="card-title"><a href="#fifth_page">밀집도 관련</a></h5>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5" id="first_page"></div>

            <div class="row">
                <h2 class="text-center mb-5 tb-5 title">
                    1. 생활 안전 관련 점수 산정 방법
                </h2>
                <div class="mt-5"></div>
                <div class="col-md-7 ps-5 w-50">
                    <div class="img_section ps-5">
                        <img src="/wherehouse/images/description1.png" alt="">
                    </div>
                </div>
                <div class="col-md-5 img_text">
                    <p class="text-end first_background">
                        생활 안전 점수 산정 방식<br><br>
                        <span class="descript_text">
                            핀 포인트를 기반으로 파출소까지의 최소 거리와 반경 500미터의 cctv 개수,<br>
                            그리고 해당 지역구의 범죄 검거율을 수집하였습니다. <br>
                            수집한 데이터의 지표 별 가중치를 다르게 하여 안전성 점수를 산정합니다. <br>
                            <br><br>
                            <span id="formula">
                                검거율 = 0.8104 + 0.006977 * 시내 주요 기관 수 - 3.374×10^(-7) * 인구 수 <br> - 6.682×10^(-6) *
                                밀집도 <br>
                                안전성 점수 = 파출소까지의 최소 거리 * 60 + 반경 500m cctv 개수 * 30 + 검거율 * 10
                            </span>
                        </span>
                    </p>

                </div>
            </div>

            <!-- 생활 편의 관련 점수&단계 산정 방식 -->
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5" id="second_page"></div>
            <div class="row">
                <h2 class="text-center mb-5 tb-5 title">
                    2. 생활 편의 관련 점수 산정 방법
                </h2>
                <div class="mt-5"></div>
                <div class="col-md-7 ps-5 w-50">
                    <div class="img_section ps-5">
                        <img src="/wherehouse/images/description2.png" alt="">
                    </div>
                </div>
                <div class="col-md-5 img_text">
                    <p class="text-end first_background">
                        생활 편의 점수 산정 방식<br><br>
                        <span class="descript_text">
							편의점, 음식점, 카페, 올리브영 등의 편의시설 데이터들을 분석한 후,<br>
                            1인가구와  상관관계가 있는 데이터만 선정해서<br>
                        각 구별 편의시설 데이터를 종합하여 편의점수를 산정합니다.
                           </span>
                    </p>
                </div>
            </div>

            <!-- 거주지 가격 관련 평균가 산정 방식 + 순위 -->
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5" id="third_page"></div>
            <div class="row">
                <h2 class="text-center mb-5 tb-5 title">
                    3. 거주지 가격 관련 평균가 산정 방식
                </h2>
                <div class="mt-5"></div>
                <div class="col-md-7 ps-5 w-50">
                    <div class="img_section ps-5">
                        <img src="/wherehouse/images/description3.png" alt="">
                    </div>
                </div>
                <div class="col-md-5 img_text">
                    <p class="text-end first_background">
                        <br>
                        평균가 산정 방식 + 순위<br><br>
                        <span class="descript_text">
                            서울시 전월세 정보 데이터를 수집하여 <br> 각 지역구 별 평균 전세금과 평균 보증금, 월세금을 산정합니다. <br>
                            거주지 추천을 받아 보실 때, 전세금과 월세금을 입력해주신다면 <br>저희 WhereHouse가 입력값과 가장 비슷한
                            가격을 형성하고 있는 <br>지역구 3곳을 정확도가 높은 순으로 추천을 드립니다.
                        </span>
                    </p>
                </div>
            </div>

            <!-- 거주지 추천 결과는 어떠한걸 기반으로 내나 -->
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5" id="fourth_page"></div>
            <div class="row">
                <h2 class="text-center mb-5 tb-5 title">
                    4. 거주지 추천 결과 도출 방법
                </h2>
                <div class="mt-5"></div>
                <div class="col-md-7 ps-5 w-50">
                    <div class="img_section ps-5">
                        <img src="/wherehouse/images/description4.png" alt="">
                    </div>
                </div>
                <div class="col-md-5 img_text">
                    <p class="text-end first_background">
                        추천 결과 내는 방법<br><br>
                        <span class="descript_text">
                            저희 WhereHouse는 입력받은 월세금 혹은 전세금과 <br>가장 비슷한 가격을 형성하고 있는 지역구 3곳을 찾습니다. <br>
                            그 다음으로 입력받은 안전성과 편의성 중 <br>어떤 것을 더 높게 입력받았는지 확인한 다음<br>
								3곳의 지역구의 추천 순서를 정하여 결과를 보여드립니다 !
                        </span>
                    </p>
                </div>
            </div>

            <!-- 인구밀집도 수치 산정 방법 -->
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5" id="fifth_page"></div>
            <div class="row">
                <h2 class="text-center mb-5 tb-5 title">
                    5. 인구밀집도 수치 산정 방법
                </h2>
                <div class="mt-5"></div>
                <div class="col-md-7 ps-5 w-50">
                    <div class="img_section ps-5">
                        <img src="/wherehouse/images/description5.png" alt="">
                    </div>
                </div>
                <div class="col-md-5 img_text">
                    <p class="text-end first_background">
                        수치 산정 방식<br><br>
                        <span class="descript_text">
                            서울시 지역구 별 인구 밀도 데이터와 지역구 별 면적 데이터를 수집한 다음, <br>면적 대비 인구 수를 정리했습니다.<br>
                            총 0~7단계로 구분 짓고, 단계가 높을수록 색상을 진하게 설정합니다. <br>
                            인구 밀도가 고루 분포하지 않고 중간 값에 많이 분포되어 있어 <br>중간 단계에 여러 지역 구가 속하도록 수치를 산정하였습니다.
                        </span>
                    </p>
                </div>
            </div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
            <div class="mt-5 mb-5 pt-5 pb-5"></div>
        </div>
    </section>

    <div id="top_btn" class="text-center mt-5">
        <a href="#" class="btn btn-outline-secondary mb-5">TOP</a>
    </div>

    <!-- </footer> -->
    <div class="container">
        <footer class="py-5 border-top border-secondary border-opacity-50 mt-5">
            <div class="row">
                <div id="" class="footer_text col-md-4 mb-3 text-left">
                    <h4 class="mb-4 ms-4">Contact</h4>
                    <ul class="nav-flex-column">
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted">
                                <i class="bi bi-geo-alt-fill">
                                    서울시 서대문구 증가로
                                </i>
                            </a>
                        </div>
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted">
                                <i class="bi bi-telephone-fill">
                                	팀장 이재서 : 010-4130-1998<br>
                                    &nbsp;&nbsp;&nbsp;&nbsp;
                                    조원 정범진 : 010-3370-7750
                                </i>
                            </a>
                        </div>
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted" href="https://github.com/bumjinDev/wherehouse_SpringBoot"
                                target="_blank">
                                <i class="bi bi-github">
                                    github.com/WhereHouse
                                </i>
                            </a>
                        </div>
                    </ul>
                </div>

                <div id="" class="footer_text col-md-4 mb-3 text-left">
                    <h4 class="mb-4 ms-4">Our Project Plan</h4>
                    <ul class="nav-flex-column">
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted"
                                href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/1.%ED%94%84%EB%A1%9C%EC%A0%9D%ED%8A%B8%20%EA%B8%B0%ED%9A%8D%EC%84%9C"
                                target='_blank'>
                                Project Plan pres.
                            </a>
                        </div>
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted"
                                href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/2.%EA%B0%9C%EB%B0%9C%EA%B3%84%ED%9A%8D%EC%84%9C"
                                target='_blank'>
                                Develop Plan
                            </a>
                        </div>
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted"
                                href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/3.%EC%9A%94%EA%B5%AC%EC%82%AC%ED%95%AD%20%EC%A0%95%EC%9D%98%EC%84%9C_%EB%B6%84%EC%84%9D%EC%84%9C"
                                target='_blank'>
                                Function Specification
                            </a>
                        </div>
                        <div class="nav-item mb-2">
                            <a class="nav-link p-0 text-muted"
                                href="https://github.com/bumjinDev/wherehouse_SpringBoot/tree/master/4.%ED%99%94%EB%A9%B4%20%EC%84%A4%EA%B3%84%EC%84%9C"
                                target='_blank'>
                                Wire Frame
                            </a>
                        </div>
                    </ul>
                </div>
                <div class="col-md-4 mb-3 text-center">
                    <img src="/wherehouse/images/home_icon.png" alt="" class="w-50 h-75 mt-3">
                </div>
            </div>
        </footer>
    </div>
</body>

</html>