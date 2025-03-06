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

function deletePost() {
    var boardId = document.getElementById('boardId').value;

    fetch(`/wherehouse/delete/${boardId}`, { method: "DELETE" })
        .then(response => {
            if (!response.ok) {
                return response.json().then(err => { throw err; }); // RFC 7807 기반 오류 처리
            }
            return response; // 성공 시 추가 데이터 없음
        })
        .then(() => {
            alert("글 삭제 완료!");
            window.location.href = "/wherehouse/list/0"; // 게시판 목록으로 이동
        })
        .catch(error => {
            alert(`${error.title}: ${error.detail}`); // RFC 7807 기반 오류 메시지 출력
        });
}
