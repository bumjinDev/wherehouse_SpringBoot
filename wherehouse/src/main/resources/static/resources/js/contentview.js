window.onload = function(){

	/* 게시글 수정 페이지 "contentedit.jsp"로 이동. */
	document.querySelector('.editbutton').addEventListener('click', function(){
		/* 로그인한 사용자 계정과 세션 아이디가 일치해야지만 수정 페이지 이동 허용*/
		var writerId = document.querySelector('.writerId').value; 	// 게시글 작성한 사용자 ID 정보
		var sessionId = document.querySelector('.sessionId').value;   // 현재 seesion 내 로그인한 사용자 ID 정보
	
		if (writerId == sessionId) {		/* 현재 로그인 한 계정과 게시글 작성 사용자가 일치하면 동작 */
		
			console.log('수정 페이지 "contentedit.jsp" 이동');
			 document.getElementById('modifyform').submit();
	
		} else {						/* 현재 로그인한 계정과 게시글 작성 사용자가 다르면 그냥 경고창만 띄움 */
	
			alert("현재 사용자는 게시글 작성자와 다릅니다.");
		}
	});

	document.querySelector('.deletebutton').addEventListener('click', function(){
		var sessionId = document.querySelector('.sessionId').value;   	// 현재 seesion 내 로그인한 사용자 ID 정보
		var writerId = document.querySelector('.writerId').value; 			// 게시글 작성한 사용자 ID 정보
	
	
		if (writerId == sessionId) {
	
			var bId = document.querySelector('.bId').value;
	
			alert("현재 글을 삭제합니다.");
			window.location.href = `/wherehouse/delete/${bId}`;
	
		} else if (writerId != sessionId) {
	
			alert("현재 사용자와 게시글 작성자가 다르므로 삭제할 수 없습니다.");
			event.preventDefault();
			return;
		}
	});
	
	/* 게시글 전체 목록 "list.jsp" 이동. */
	document.querySelector('.listbutton').addEventListener('click', function(){
	
		alert("전체 글 목록으로 이동합니다.");
		window.location.href = '/wherehouse/page/list';
	});

	document.querySelector('.replybutton').addEventListener('click', function(){
	
		var sessionId = document.querySelector('.sessionId').value;
		var replyvalue = document.querySelector('.replyvalue').value;

		
		if(sessionId == 'null'){
			
			alert("로그인 한 사용자가 아닙니다");	
		} else if (replyvalue == "" ) {						/* 댓글 입력 창이 공백이라면 게시글 메시지만 띄우기*/
	
			alert("댓글 작성 내용이 없습니다!!.");
		} else {
	
			/* db 내 댓글 추가 */
			document.getElementById('replyform').submit();
	
			alert("댓글이 추가되었습니다.");
		}
	});
}