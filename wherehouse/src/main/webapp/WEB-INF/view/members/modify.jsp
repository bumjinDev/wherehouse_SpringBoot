<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
 
 <% request.setCharacterEncoding("UTF-8"); String id=(String)session.getAttribute("id"); %>

 <!DOCTYPE html>
                <html lang="en">

                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <link rel="icon" href="images/home_icon.png">
                    <link rel="stylesheet" href="./css/modify.css">

                    <script language="javascript" src="js/modify.js"></script>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link href="https://fonts.googleapis.com/css2?family=Courgette&display=swap" rel="stylesheet">
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link href="https://fonts.googleapis.com/css2?family=Black+Han+Sans&display=swap" rel="stylesheet">

                    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/css/bootstrap.min.css"
                        rel="stylesheet"
                        integrity="sha384-4bw+/aepP/YC94hEpVNVgiZdgIC5+VKNBQNGCHeKRQN+PtmoHDEXuppvnDJzQIu9"
                        crossorigin="anonymous">
                    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.1/dist/js/bootstrap.bundle.min.js"
                        integrity="sha384-HwwvtgBNo3bZJJLYd8oVXjrBZt8cqVSpeBNS5n7C8IVInixGAoxmnlMuBnhbgrkm"
                        crossorigin="anonymous"></script>
                    <link rel="stylesheet"
                        href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.7.1/font/bootstrap-icons.css">
                    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>	<!-- jquery 추가! -->
                    <title>WhereHouse</title>
                </head>

                <body>
                    <div class="container">
                        <header>
                            <div id="logo">
                                <div class=" mb-5"></div>
                                <a id="logo_text" href="/wherehouse">Where House</a>
                            </div>
                        </header>
                        <div class="mt-4 mb-4"></div>
                        <section class="text-center" id="modify-all-section">

                            <div id="inputBox" class="me-3 ms-3 row">
                                <div id="login-form-border" class="pt-3 pb-3">
                                    <form action="./membermodifyok" method="post" id="reg_frm" >
                                        <div class=" mt-3 mb-1" id="modify-id"><input type="text" size="20"
                                                class="form-control" value="${MembersVO.id}" disabled>
                                        		<input type="hidden" name="id"  value="${MembersVO.id}" />
                                        </div>						<!-- Id 항목, 수정 불가-->
                                        <div class="mb-1" id="modify-pw"><input type="password" name="pw"  id ="pw" size="20"
                                                class="form-control"
                                                placeholder="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;비밀번호"></div>			<!-- Password -->
                                                
                                        <div class="mb-5" id="modify-pwCheck"><input type="password"  id ="pwcheck"
                                                size="20" class="form-control"
                                                placeholder="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;비밀번호 확인"></div>	<!-- Password check -->
                                                
                                        <div class="mb-1" id="modify-nickName"><input type="text" name="nickName" id ="nickname" 
                                                size="20" class="form-control"
                                                placeholder="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;닉네임"></div>			<!-- nickname -->
                                                
                                        <div class="mb-1" id="modify-tel"><input type="text" name="tel" size="20"	 id ="tel" 
                                                class="form-control"
                                                placeholder="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;전화번호"></div>			<!-- 전화번호 -->
                                                
                                        <div class="mb-2" id="modify-email"><input type="text" name="email" size="20"
                                                class="form-control"
                                                placeholder="&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;이메일"></div>			<!-- 이메일 -->

                                        <div id="logo-img"><img src="images/home_icon.png" alt=""></div>
                                        <div class="mt- mb- pb-2"></div>
                                        <div id="modify-btn" class="button-login-box">
                                            <input type="button" id="memberEditBtn" value="회원 정보 수정"
                                                class="btn btn-primary btn-xs mt- mb-2" style="width:100%; height:60px;"></input>
                                        </div>
                                        <div id="modify-btn" class="button-login-box">
                                            <input type="reset" value="취소" class="btn btn-primary btn-xs mt- mb-4"
                                                style="width:100%; height:60px;"
                                                onclick="javascript:window.location='./loginSuccess'"></input>
                                        </div>
                                    </form>
                                </div>
                            </div>
                        </section>
                    </div>
                </body>

                </html>