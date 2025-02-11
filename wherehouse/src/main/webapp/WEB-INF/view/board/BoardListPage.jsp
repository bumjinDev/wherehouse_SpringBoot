<%@page import="ch.qos.logback.core.net.SyslogOutputStream"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

   <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
      <% 
      	 // String nickname=(String) session.getAttribute("nickname");	// 기존의 로그인 세션 여부 확인.
      	 int pnSize = Integer.parseInt(String.valueOf(request.getAttribute("pnSize")));      // pnSize : 요청 처리 후에 전체 페이지 네이션 크기를 주어야지만  브라우저가 페이지 내 버튼을 몇개 생성할지 알 수 있게 하기 위한 목적.
      %>
         <!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
         <html lang="ko">

         <head>
            <meta http-equiv="Content-Type" charset="text/html;charset=UTF-8">

            <!-- 부트 스트랩 -->
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css" rel="stylesheet"
               integrity="sha384-EVSTQN3/azprG1Anm3QDgpJLIm9Nao0Yz1ztcQTwFspd3yD65VohhpuuCOmLASjC"
               crossorigin="anonymous">
            <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/js/bootstrap.bundle.min.js"
               integrity="sha384-MrcW6ZMFYlzcLA8Nl+NtUVF0sA7MsXsP1UyJoMp4YLEuNSfAP+JcXn/tWtIaxVXM"
               crossorigin="anonymous"></script>

            <script language="JavaScript" src="/wherehouse/js/BoardListPage.js"></script>

            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.11.2/jquery.min.js"></script>
            <script src="https://kit.fontawesome.com/09b067fdc5.js" crossorigin="anonymous"></script>
            <link rel="stylesheet" href="/wherehouse/css/list.css?ver=123">
            <link rel="stylesheet" href="/wherehouse/css/listshow.css">
            <!-- <script src="js/list.js?ver=123"></script> 패널 열고 닫는 버튼 사용하지 않음. -->

         </head>

         <body>

            <h1 class="title">WhereHouse 게시판</h1>
            <!-- 게시판 목록 보여주는 화면. -->
            <div class="showlist">
               <table id="boardtable" class="table table-bordered border-dark">
                  <thead>
                     <tr class=" table-primary">
                        <th scope="col">글번호</th>
                        <th scope="col">제목</th>
                        <th scope="col">닉네임</th>
                        <th scope="col">지역구</th>
                        <th scope="col">조회수</th>
                        <th scope="col">날짜</th>
                     </tr>
                  </thead>
                  <tbody>
                     <c:forEach var="boardList" items="${boardList}" varStatus="status">
                        <tr>
                           <td>${boardList.connum}</td>
                           <td><a href="/wherehouse/choiceboard/${boardList.connum}">${boardList.title}</a></td>
                           <td>${members[status.index]}</td>
                           <td>${boardList.region}</td>
                           <td>${boardList.hit}</td>
                           <td>${boardList.bdate}</td>
                        </tr>
                     </c:forEach>
                  </tbody>
               </table>
          		
          	   <button class="writebtn" id="writebtn" type="button">글 작성</button>
               
               <div class="paginationbtn">
                  <c:forEach var="i" begin="1" end="<%=pnSize %>">
                   <button class="${i}">${i}</button>
                  </c:forEach>
               </div>
            </div>   
         </body>
      </html>