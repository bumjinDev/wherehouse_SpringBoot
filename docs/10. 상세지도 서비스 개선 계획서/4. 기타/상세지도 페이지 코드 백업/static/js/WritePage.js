window.onload = function() {
	/* 글 작성 버튼 */
    document.getElementById('boardWrite').addEventListener('click', requestBoardWrite);
};

/* 글 작성 수행 메소드 */
async function requestBoardWrite() {
	
    var boardTitle = document.getElementById("boardTitle").value;
    var boardContent = document.getElementById("boardContent").value;
    var boardRegion = document.getElementById("boardRegion").value;

    /* 클라이언트 입력 검증 */
    if (boardTitle.trim() === '') {
        alert("제목을 입력 하세요."); return;
    }
    if (boardContent.trim() === '') {
        alert("내용을 입력 하세요."); return;
    }

	/* 글 작성 시 요청할 JSON 데이터를 JSON 형태로 포멧 */
    const payload = {
		
        title: boardTitle,
        board_content: boardContent,
        region: boardRegion
    };

    try {
		
        const httpResponse = await fetch('/wherehouse/boards/', {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (httpResponse.ok) {
            // 서버 응답을 JSON 객체로 파싱
            const responseData = await httpResponse.json();
            const boardId = responseData.boardId;

            alert('게시글 작성 완료 되었습니다! 해당 게시글로 이동 합니다.');
            window.location.href = '/wherehouse/boards/' + boardId;
        } 
        else if (httpResponse.status === 400) {
            // 유효성 검증 실패 시, 에러 메시지를 JSON 형태로 파싱
            const errorData = await httpResponse.json();
            const messages = Object.values(errorData).join(' ');
            alert(messages);
        } 
        else { alert('서버 측 알 수 없는 에러 발생 했습니다.'); }

    } catch (error) {
		
        alert('서버와 통신 중 문제가 발생했습니다.');
        console.error("요청 실패:", error);
    }
}
