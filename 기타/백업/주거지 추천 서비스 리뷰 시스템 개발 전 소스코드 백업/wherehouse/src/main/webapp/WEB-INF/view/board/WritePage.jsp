<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
	<!DOCTYPE html>
	<html>

	<head>
		<meta http-equiv="Content-Type" charset="text/html;charset=UTF-8">
		<script language="JavaScript" src="/wherehouse/js/WritePage.js"></script>
		<title>Insert title here</title>
		<link rel="stylesheet" href="/wherehouse/css/list.css?ver=123">
		<link rel="stylesheet" href="/wherehouse/css/write.css">
	</head>

	<body>
		<table border="1" class="writetbl">
		
			<!--
			글 작성 시점 요청 데이터 :
			- 글 제목
			- 글 지역
			- 글 내용
			- 작성자 Id
			 -->
		
			<input type="hidden" id="userId" value="${userId}">	<!-- 사용자 ID -->
			
			<h1>WhereHouse 글 쓰기</h1>
			<tr>
				<td class="narrow">이름</td>
				<td>${userName}</td>
			</tr>

			<tr>
				<td class="narrow">제목</td>
				<td><textarea id="boardTitle" class="title" rows="1" cols="50"></textarea></td>
			</tr>
			<tr>
				<td class="narrow">
					<label for="regions">지역 선택하세요</label>
				</td>
				<td>
					<select id="boardRegion" name="region" class="regions">
						<option value="미 선택">지역구를 선택해주세요</option>
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
				<td><textarea name="boardContent" id="boardContent" class="bcontent" rows="15" cols="50"></textarea></td>
			</tr>
			<tr>
				<td colspan="2"><input type="button" id="boardWrite" value="글 작성">&nbsp;&nbsp;<a href="/wherehouse/boards/page/0">목록으로 돌아가기</a></td>
			</tr>
		</table>
	</body>

	</html>