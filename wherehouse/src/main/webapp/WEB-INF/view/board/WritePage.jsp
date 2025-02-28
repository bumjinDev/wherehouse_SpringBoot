<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
	<!DOCTYPE html>
	<html>

	<head>
		<meta http-equiv="Content-Type" charset="text/html;charset=UTF-8">
		<script language="JavaScript" src="./js/WritePage.js"></script>
		<title>Insert title here</title>
		<link rel="stylesheet" href="./css/list.css?ver=123">
		<link rel="stylesheet" href="./css/write.css">
	</head>

	<body>
		<table border="1" class="writetbl">
			<form action="./boardwrite" name="writefrm" method="post">
			
			
				<input type="hidden" name="userid" value="${requestScope.userId}">	<!-- 사용자 ID -->
				<!-- 글 제목 : 본문 데이터 사용. name="title" -->
				<!-- 지역 선택 : 본문 데이터 사용, name="regions" -->
				<!-- 글 본문 : 본문 데이터 사용, name="boardcontent" -->
				
				<h1>WhereHouse 글 쓰기</h1>
				<tr>
					<td class="narrow">이름</td>
					<td>${requestScope.userName}</td>
				</tr>

				<tr>
					<td class="narrow">제목</td>
					<td><textarea name="title" class="title" rows="1" cols="50"></textarea></td>
				</tr>
				<tr>
					<td class="narrow">
						<label for="regions">지역 선택하세요</label>
					</td>
					<td>
						<select name="regions" class="regions">
							<option value="선택 지역 없음">지역구를 선택해주세요</option>
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
					</td>
				</tr>
				<tr>
					<td class="narrow">내용</td>
					<td><textarea name="boardcontent" class="bcontent" rows="15" cols="50"></textarea></td>
				</tr>
				<tr>
					<td colspan="2"><input type="button" id="writeBtn" value="글 쓰기 완료">&nbsp;&nbsp;<a href="/wherehouse/list/0">목록으로 돌아가기</a></td>
				</tr>
			</form>
		</table>
	</body>

	</html>