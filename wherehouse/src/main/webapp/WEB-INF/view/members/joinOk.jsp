<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%
	request.setCharacterEncoding("UTF-8");
%>

<%
	String resInt = (String) request.getAttribute("resInt");

	/* resInt : join.jsp 요청 결과, 1 : 이미 존재하는 회원, 0 : 정상 회원 가입 요청*/
	if(resInt.equals("1")){			
%>
		<script langauage="javascript">
			alert("아이디가 이미 존재 합니다.");
			
			document.location.href = "./page/join";
		</script>
<%
	} else {	
%>
		<script language="javascript">
			alert("회원가입이 정상적으로 되었습니다.");
			document.location.href = "./page/login";
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