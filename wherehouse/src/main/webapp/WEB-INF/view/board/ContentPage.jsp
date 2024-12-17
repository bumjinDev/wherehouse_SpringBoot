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
				<form action="/wherehouse/modifypage" id="modifyform" method="post">
					
					<!-- form 태그 적용되어 서버 내 요청될 내용들 -->
					<input type="hidden" name="boardId" id="boardId" value="${content_view.connum}">		<!-- 글 번호 -->		
					<input type="hidden" name="title" value="${content_view.title}" />						<!-- 글 제목 -->
					<input type="hidden" name="boardContent"value="${content_view.boardcontent}" />			<!-- 글 내용 -->
					<input type="hidden" name="region" value="${content_view.region}" />					<!-- 게시글 지역 -->
					<input type="hidden" name="boardDate" value="${content_view.bdate}" />					<!-- 게시글 작성 날짜 -->
					<input type="hidden" name="boardHit" value="${content_view.hit}" />						<!-- 게시글 조회수 -->
					<input type="hidden" name="AuthorNickname" value="${AuthorNickname}">					<!-- 글 작성자 ID -->
					<input type="hidden" name="writerId" id="content_view.userid" value="${content_view.userid}">					<!-- 글 작성자 ID -->

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
								<th class="attributeBox">작성자</th><th class="valueBox">${AuthorNickname}</th>
							</tr>
							<tr>	<!-- 게시글 지역 작성 내용 -->
								<th class="attributeBox">게시글 지역</th><th class="valueBox">${content_view.region}</th>
							</tr>
							<tr>	<!-- 게시글 작성 날짜 --> <!--  -->
								<th class="attributeBox">작성 날짜</th><th class="valueBox">${content_view.bdate}</th>
							</tr>
							<tr>	<!-- 게시글 조회수 -->
								<th class="attributeBox">조회수</th><th class="valueBox">${content_view.hit}</th>
							</tr>
						</table>
						
					<!-- 게시글 본문 들어가는 부분-->
					<div class="boardContent">
						<table class = "tmptbl">
							<tr>
								<th class="boardhead"><p class ="btitle">글 내용</p></th><td class="boardbody"><textarea class="bcontent" readonly>${content_view.boardcontent}</textarea></td>
							</tr>
						</table>
						
					</div>

					<button value="글 페이지로 이동하기" type="button" class="editbutton" style="width:100px; heigth:50px;">수정 페이지</button>
					<button value="해당 글 삭제하기" type="button" class="deletebutton" style="width:100px; heigth:50px;">
						글 삭제</button> 	
					<button value="전체 글 목록 보기" type="button" class="listbutton" style="width:100px; heigth:50px;">
						전체 글 확인</button> 	<!-- list.do 요청 -->
						
				</form>
				
				<!-- 댓글 관련 내용 -->
				<div class="commenthead">댓글 작성 및 확인</div>
				
				<!-- 댓글 작성 요청 -->
				<form action="/wherehouse/replyWrite" id="replyform" method="post">
			
					<!-- form 태그 적용되어 서버 내 요청될 내용들 -->
					
					<input type="hidden" name="boardId" id="boardId" value="${content_view.connum}">	<!-- 글 번호 -->					
					<textarea rows="4" cols="54" name="replyContent" class="replyvalue"></textarea>		<!-- 댓글 작성되는 부분 -->
					
					<button value="댓글 작성하기" type="button" class="replybutton" style="width:100px; heigth:50px;">
						댓글 작성하기</button> 	<!-- reply.do 요청 -->
				</form>
				
			
					<!-- 게시글 댓글 목록 보이는 테이블 부분 -->
					<table class="commnetTbl" border="1">
						<tr class="showtitle">
							<td class="commentwriter" >작성자</td>
							<td class="commentbody">작성 내용</td>
						</tr>
						<c:forEach var="comments" items="${comments}">
							<tr class="showcomment">
								<td class="commentUser">${comments.nickname}</td>
								<td class="commentContent">${comments.content}</td>
							</tr>
						</c:forEach>
					</table>
				
			</body>

			</html>