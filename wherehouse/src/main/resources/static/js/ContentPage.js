window.onload = function(){
	
	/* 게시글 수정 페이지 "ContenteEit.jsp"로 이동. */
	document.querySelector('.editbutton').addEventListener('click', function(){
		
		document.getElementById('modifyform').submit();
	});

	/* 삭제 요청 시 HostOnly Cookie 내 JWT 가 서버로 전송되어 글 삭제 요청을 처리한다.
		현재 요청자와 동일하다면 삭제가 이루어지며, 동일하지 않을 시 별도의 alert JavaScript 포함한 간단한 페이지가 리다이렉트 된다. */
	document.querySelector('.deletebutton').addEventListener('click', function(){
		
		var boardId = document.getElementById('boardId').value;
		window.location.href = `/wherehouse/delete/${boardId}`;
	});
	
	/* 게시글 전체 목록 "list.jsp" 이동. */
	document.querySelector('.listbutton').addEventListener('click', function(){
	
		alert("전체 글 목록으로 이동합니다.");
		window.location.href = '/wherehouse/list/0';
	});

	document.querySelector('.replybutton').addEventListener('click', function(){
		
		/* db 내 댓글 추가 */
		document.getElementById('replyform').submit();	
	});
}