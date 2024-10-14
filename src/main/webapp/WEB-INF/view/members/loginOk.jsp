<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
	request.setCharacterEncoding("UTF-8");

	String loginRes = (String) request.getAttribute("loginRes");

	if(loginRes.equals("-1")){
%>
	<script language="javascript">
		alert("아이디가 존재하지 않습니다.");
		history.go(-1);
	</script>
<%
	} else if(loginRes.equals("0")) {
%>
	<script language="javascript">
		alert("비밀번호가 틀립니다");
		history.go(-1);
	</script>
<%
	} else if(loginRes.equals("1")){			// id와 패스워드 모두 동일하면 dto 내 객체 저장.
		
		response.sendRedirect("./loginSuccess");
	}
%>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta charset="UTF-8">
<title>html 본문 사용하지 않음.</title>
</head>
<body>
</body>
</html>