window.onload = function () {
	
	var redirectUrl = document.getElementById('redirectUrl').value;
	
	console.log('redirectUrl : ' + redirectUrl);
	
	if(redirectUrl === '/wherehouse/list/0'){
		alert('정상적으로  삭제 처리 되셨습니다.');
	}
	else {
		alert('게시글 작성자가 아니므로 삭제 불가능 합니다.')
	}
	
	window.location.href = redirectUrl;
};