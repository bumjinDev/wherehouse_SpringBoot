<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
	<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

			<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
			<html>

			<head>
				<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
				<title>게시글 수정 페이지</title>
				<script language="JavaScript" src="/wherehouse/js/ContentPage.js"></script>
				<link href="/wherehouse/css/contentView.css" rel="stylesheet">
			</head>

			<body>
				<h1 class="maintitle">Where House 게시글</h1>
				<!-- form 태그 적용되어 서버 내 요청될 내용들 : 글 수정 및 삭제 요청 시 게시글 번호 값만 전송. (현재 페이지를 요청하는 이전 단계에서 이미 가져온 데이터들.) -->
				<input type="hidden" id="boardId" value="${content_view.boardId}">			<!-- 글 번호 -->	

				<!-- 게시글 제목 : 게시글 제목 -->
				<div class="headerTitle">
					<table>
						<tr>
							<th class="titleitem"><h5>게시글 제목</h5></th><th ><textarea class="posttitle"  readonly>${content_view.title}</textarea></th>
						</tr>
					</table>
				</div>

				<!-- 2. 작성자 닉네임, 게시글 지역, 게시글 조회수, 게시글 날짜. -->
				<table class="headerTbl">
					<tr>	<!-- 작성자 닉네임 -->
						<th class="attributeBox">작성자</th><th class="valueBox">${userName}</th>
					</tr>
					<tr>	<!-- 게시글 지역 작성 내용 -->
						<th class="attributeBox">게시글 지역</th><th class="valueBox">${content_view.region}</th>
					</tr>
					<tr>	<!-- 게시글 작성 날짜 --> <!--  -->
						<th class="attributeBox">작성 날짜</th><th class="valueBox">${content_view.boardDate}</th>
					</tr>
					<tr>	<!-- 게시글 조회수 -->
						<th class="attributeBox">조회수</th><th class="valueBox">${content_view.boardHit}</th>
					</tr>
				</table>
					
				<!-- 게시글 본문 들어가는 부분-->
				<div class="boardContent">
					<table class = "tmptbl">
						<tr>
							<th class="boardhead"><p>글 내용</p></th><td class="boardbody"><textarea class="boardValue" readonly>${content_view.boardContent}</textarea></td>
						</tr>
					</table>
				</div>
					
				<!-- 글 수정 페이지 요청 -->
				<button value="글 페이지로 이동하기" type="button" id="editPage" class="editbutton" style="width:100px; heigth:50px;">수정 페이지</button>
				<!-- 글 삭제 요청 -->
				<button value="해당 글 삭제하기" type="button" id="delete" class="deletebutton" style="width:100px; heigth:50px;">글 삭제</button>
				<!-- 전체 게시글 목록으로 이동. -->
				<button value="전체 글 목록 보기" type="button" class="listbutton" style="width:100px; heigth:50px;">전체 글 확인</button>
				
				
				<!-- 댓글 관련 내용 -->
				<div class="commenthead">댓글 작성 및 확인</div>
				
				<!-- 서버 내 댓글 작성 요청으로 전달 될 내용들 -->
				<input type="hidden" id="boardId" value="${content_view.boardId}">					<!-- 글 번호 -->			
				<textarea rows="4" cols="54" id="replyContent" class="replyvalue"></textarea>		<!-- 댓글 작성되는 부분 -->
				
				<button value="댓글 작성하기" type="button" class="replybutton" style="width:100px; heigth:50px;">
					댓글 작성하기</button> 	<!-- reply.do 요청 -->
				
			
					<!-- 게시글 댓글 목록 보이는 테이블 부분 -->
					<table class="commnetTbl" border="1">
						<tr class="showtitle">
							<td class="commentwriter" >작성자</td>
							<td class="commentbody">작성 내용</td>
						</tr>
						<c:forEach var="comments" items="${comments}">
							<tr class="showcomment">
								<td class="commentUser">${comments.userName}</td>
								<td class="commentContent">${comments.replyContent}</td>
							</tr>
						</c:forEach>
					</table>
			</body>

			</html>