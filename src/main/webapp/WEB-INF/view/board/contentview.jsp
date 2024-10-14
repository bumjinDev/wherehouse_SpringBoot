<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
	<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

		<% String sessionId=(String) session.getAttribute("id"); String sessionNickname=(String)
			session.getAttribute("nickname"); %>

			<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
			<html>

			<head>
				<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
				<title>게시글 수정 페이지</title>
				<script language="JavaScript" src="/wherehouse/js/contentview.js"></script>
				<link href="/wherehouse/css/contentView.css" rel="stylesheet">
			</head>

			<body>
				<h1 class="maintitle">Where House 게시글</h1>
				<form action="../modifypage" id="modifyform" method="post">

					<!-- sessionId/writeId : 수정 버튼 클릭 시 "Contentview.js" 파일에서 "modifycheck()" 함수 통해 게시글 사용자와 현재 로그인
						사용자가 동일한지 확인하기 위한 값. -->
					<input type="hidden" name="sessionId" class="sessionId" value="<%=sessionId %>"> <!-- 현재 로그인 ID -->
					<input type="hidden" name="writerId" class="writerId" value="${content_view.userid}">

					<!-- form 태그 적용되어 서버 내 요청될 내용들 -->
					<input type="hidden" name="bId" class="bId" value="${content_view.contentnum}">						<!-- 글 번호 -->					
					<input type="hidden" name="title" class="title" value="${content_view.title}" />								<!-- 글 제목 -->
					<input type="hidden" name="boardContent" class="bcontent" value="${content_view.bcontent}" />	<!-- 글 내용 -->
					<input type="hidden" name="region" class="region" value="${content_view.region}" />					<!-- 게시글 지역 -->
					<input type="hidden" name="boardDate" class="bDate" value="${content_view.bdate}" />				<!-- 게시글 작성 날짜 -->
					<input type="hidden" name="boardHit" class="hit" value="${content_view.hit}" />							<!-- 게시글 조회수 -->
	
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
								<th class="attributeBox">작성자</th><th class="valueBox">${nickname}</th>
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
								<th class="boardhead"><p class ="btitle">글 내용</p></th><td class="boardbody"><textarea class="bcontent" readonly>${content_view.bcontent}</textarea></td>
							</tr>
						</table>
						
					</div>

					<button value="글 페이지로 이동하기" type="button" class="editbutton" style="width:100px; heigth:50px;">수정 페이지</button>
					<button value="해당 글 삭제하기" type="button" class="deletebutton" style="width:100px; heigth:50px;">
						글 삭제</button> 	<!-- 현재 sessionId와 게시글 작성 id를 비교하여 일치 해야지만 삭제 -->
					<button value="전체 글 목록 보기" type="button" class="listbutton" style="width:100px; heigth:50px;">
						전체 글 확인</button> 	<!-- list.do 요청 -->
						
				</form>
				
				<!-- 댓글 관련 내용 -->
				<div class="commenthead">댓글 작성 및 확인</div>
				
				<!-- 댓글 작성 요청 -->
				<form action="../replyWrite" id="replyform" method="post">
				
					<input type="hidden" name="sessionId" class="sessionId" value="<%=sessionId %>"> <!-- 현재 로그인 ID -->
					<input type="hidden" name="writerId" class="writerId" value="${content_view.userid}">

					<!-- form 태그 적용되어 서버 내 요청될 내용들 -->
					<input type="hidden" name="bId" class="bId" value="${content_view.contentnum}">						<!-- 글 번호 -->					
					<input type="hidden" name="title" class="title" value="${content_view.title}" />								<!-- 글 제목 -->
					<input type="hidden" name="boardContent" class="bcontent" value="${content_view.bcontent}" />	<!-- 글 내용 -->
				
					<!-- 댓글 작성되는 부분 -->
					<textarea rows="4" cols="54" name="replyvalue" class="replyvalue"></textarea>
					
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