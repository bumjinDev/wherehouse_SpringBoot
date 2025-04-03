document.addEventListener("DOMContentLoaded", function () {
    
    // Flash Attribute 메시지가 있으면 alert 실행
    showAlertIfExists();

    // 게시글 수정 버튼 클릭 이벤트
    document.querySelector('.editbutton')?.addEventListener('click', function () {
        document.getElementById('modifyform').submit();
    });

    // 게시글 삭제 버튼 클릭 이벤트
    document.querySelector('.deletebutton')?.addEventListener('click', deletePost);

    // 게시글 전체 목록 이동 버튼 이벤트
    document.querySelector('.listbutton')?.addEventListener('click', function () {
        alert("전체 글 목록으로 이동합니다.");
        window.location.href = '/wherehouse/list/0';
    });

    // 댓글 추가 버튼 이벤트
    document.querySelector('.replybutton')?.addEventListener('click', function () {
        document.getElementById('replyform').submit();
    });

});

// FlashAttribute의 alert 메시지 확인 및 출력 함수
function showAlertIfExists() {
    let alertMessage = document.getElementById('alertMessage')?.value;
    if (alertMessage) {
        alert(alertMessage);
    }
}

 async function deletePost() {
	
    var boardId = document.getElementById('boardId').value;

	try {
		
		const response = await fetch("/wherehouse/delete/${boardId}", { 
			method: "DELETE",
		});	
		if (response.ok) {
			
			alert("글 수정이 정상적으로 수행 되었습니다");
			window.location.href = "/wherehouse/list/0";
			
		} else if(response.status === 403) {
			
			const res = await response.json();
			const messages = Object.values(res).join('\n');
			alert("[권한 오류] : " + messages);
			
		} else { alert("알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");	}
		
	} catch (error) {
		console.error("서버 오류:", error);
		alert("서버와 통신 중 오류가 발생했습니다.");
	}
	
	
	/* 권한 확인과 실제 삭제를 별도의 2번 요청 없이 한번에 수행 : @RestController 로 요청 할 때 별도의 페이지 봔한 등 추가적인 작업이 필요 없기 때문 */
    fetch(`/wherehouse/delete/${boardId}`, { method: "DELETE" })
	
        .then(response => {
			
            if (!response.ok) { return response.json().then(err => { throw err; }); }
			else { return response; } // 성공 시 추가 데이터 없음
			
        }).then(() => {
			
            alert("글 삭제 되었습니다.");
            window.location.href = "/wherehouse/list/0"; // 게시판 목록으로 이동
			
        }) .catch(error => {
            alert(`${error.title}: ${error.detail}`); // RFC 7807 기반 오류 메시지 출력
        });
}
