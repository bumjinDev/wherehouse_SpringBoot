window.onload = function() {
	
	document.querySelector('.editbutton').addEventListener('click', function(){

		alert("게시글 수정 합니다.");
		this.submit();
	});

	document.querySelector('.listbutton').addEventListener('click', function(){

		alert("전체 글 목록으로 이동합니다.");
		window.location.href = '/wherehouse/list/0';
	});
}