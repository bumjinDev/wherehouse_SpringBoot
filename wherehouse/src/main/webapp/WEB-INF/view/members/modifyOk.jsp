<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
	request.setCharacterEncoding("UTF-8");
	int ri = (Integer) request.getAttribute("ri");
 %>
 
<%
	if(ri == 1 ){
%>
	
	<script language="javascript">
		alert("정보 수정되었습니다.");
		document.location.href="./loginSuccess"
	</script>
<%
	} else if(ri == 2 ){
%>
	<script language="javascript">
		alert("회원 수정 요청 정보 중 닉네임 중복으로 수정 실패입니다, 다른 닉네임을 사용하세요.");
		history.go(-1);
	</script>
<%
	} else {
%>
	<script language="javascript">
		alert("알 수 업는 이유로 정보 수정 실패 입니다.");
		history.go(-1);
	</script>
<%
	}
%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>

</body>
</html>