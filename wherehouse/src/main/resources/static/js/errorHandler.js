
window.onload = function() {
	// 실패 유형 읽기
	const errorType = document.getElementById("errorType").value;
	
	console.log('errorType : ' + errorType);
	
	if (errorType === "UsernameNotFoundException") {
	    alert("아이디를 찾을 수 없습니다. 다시 확인해주세요.");
	} else if (errorType === "BadCredentialsException") {
	    alert("비밀번호가 올바르지 않습니다. 다시 시도해주세요.");
	} else {
	    alert("알 수 없는 인증 오류가 발생했습니다. 관리자에게 문의하세요!");
	}
	
	// 로그인 페이지로 리다이렉트
	window.location.href = "/wherehouse/loginpage";
};
