/* 브라우저 렌더링 완료 후 이벤트 핸들러 등록 */
window.onload = function () {

    /* 게시글 수정 요청 버튼에 이벤트 핸들러 등록 */
    document.getElementById('pageEdit').addEventListener('click', requestBoardModify);  

    /* 게시글 서비스 초기 페이지로 이동 요청 */
    document.getElementById('pageList').addEventListener('click', function () {
        alert("전체 글 목록으로 이동합니다.");
        window.location.href = '/wherehouse/boards/page/0';
    });
};

/* 글 수정 요청 메소드: '글 수정' 버튼 클릭 시 호출되는 비동기 함수 */
async function requestBoardModify() {

    const boardId = document.getElementById('boardId').value;
	const title = document.getElementById('boardTitle').value;
	const region = document.getElementById('boardRegion').value;
	const boardContent = document.getElementById('boardContent').value;
	
    /* 요청 페이로드 구성: 서버 DTO 필드에 맞춰 key 매핑 */
    const payload = {
		
        board_id: boardId,
        title: title,
        region: region,
        board_content: boardContent
    };

    try {
        /* 글 수정 요청: JSON 형식으로 POST 전송 */
        const httpResponse = await fetch("/wherehouse/boards/" + boardId, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
				"Accept": "application/json"
            },
            body: JSON.stringify(payload)
        });

		if(httpResponse.ok) {
			/* 게시글 수정 성공: 게시글 상세 보기 페이지로 이동 */
	        alert('게시글 수정이 완료되었습니다.');
	        window.location.href = "/wherehouse/boards/" + boardId;		// 작성한 게시글에 대한 상세 페이지 요청
		}
		
        /* 게시글 수정 실패: 사용자 요청 정보 누락 등 잘못된 요청에 대한 서버 응답 비정상
			(권한 문제 발생 시 별도의 자동 리다이렉트 처리) */
		else if (httpResponse.status === 400) {
            // 유효성 검증 실패 시, 에러 메시지를 JSON 형태로 파싱
            const errorData = await httpResponse.json();
            const messages = Object.values(errorData).join(' ');
            alert(messages);
        } 
		else { alert('서버 측 알 수 없는 에러 발생 했습니다.'); }
    }
	/* 네트워크 오류 또는 서버와의 통신 실패 */
	catch (error) {
        
        alert('서버와 통신 중 문제가 발생했습니다.');
        console.log("요청 실패:", error);
    }
}
