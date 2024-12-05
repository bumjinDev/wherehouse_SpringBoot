window.onload = function () {
	
	var redirectUrl = document.getElementById('redirectUrl').value;
	
	console.log('redirectUrl : ' + redirectUrl);
	
	alert('게시글 작성자가 아니므로 수정은 불가능 합니다.')

	window.location.href = redirectUrl;
};