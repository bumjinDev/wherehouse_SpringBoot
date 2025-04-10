document.addEventListener("DOMContentLoaded", function () {

    // 게시글 전체 목록 이동 버튼 이벤트
    document.querySelector('.listbutton')?.addEventListener('click', function () {
        alert("전체 글 목록으로 이동합니다.");
        window.location.href = '/wherehouse/boards/page/0';
    });
    // 댓글 작성 버튼 이벤트 핸들러 등록
    document.querySelector('.replybutton')?.addEventListener('click', writeReply);
    // 게시글 수정 페이지 요청 이벤트
    document.getElementById('editPage').addEventListener('click', requestModifyPage);
    // 게시글 삭제 요청 이벤트
    document.getElementById('delete').addEventListener('click', deleteBoard);
});


/* 게시글 수정 페이지 요청 */
async function requestModifyPage() {
    const boardId = document.getElementById('boardId').value;

    try {
        const httpResponse = await fetch(`/wherehouse/boards/${boardId}/auth`, {
            method: "GET",
            headers: {
                "Accept": "application/json"
            }
        });

        if (httpResponse.ok) {
            window.location.href = `/wherehouse/boards/edit?boardId=${boardId}`;
        } else if (httpResponse.status === 401) {
            alert('올바른 로그인 사용자가 아닙니다.');
        } else if (httpResponse.status === 403 || httpResponse.status === 404) {
            const contentType = httpResponse.headers.get("content-type") || "";
            if (contentType.includes("application/json")) {
                const res = await httpResponse.json();
                const messages = Object.values(res).join(' ');
                alert(messages);
            } else {
                const errorText = await httpResponse.text();
                alert(errorText);
            }
        } else {
            alert("알 수 없는 오류가 발생했습니다.");
        }
    } catch (error) {
        console.error("요청 실패:", error);
        alert("서버와 통신 중 문제가 발생했습니다.");
    }
}


/* 게시글 삭제 */
async function deleteBoard() {
    const boardId = document.getElementById('boardId').value;
    const url = "/wherehouse/boards/" + boardId;

    try {
        const httpResponse = await fetch(url, {
            method: "DELETE",
        });

        if (httpResponse.ok) {
            alert("글 삭제 요청이 정상적으로 이루어졌습니다.");
            window.location.href = "/wherehouse/boards/page/0";
        } else if (httpResponse.status == 401) {
            alert('올바른 로그인 사용자가 아니므로 글 삭제할 수 없습니다.');
        } else if (httpResponse.status === 403 || httpResponse.status === 404) {
            const contentType = httpResponse.headers.get("content-type") || "";
            if (contentType.includes("application/json")) {
                const res = await httpResponse.json();
                const messages = Object.values(res).join(' ');
                alert(messages);
            } else {
                const errorText = await httpResponse.text();
                alert(errorText);
            }
        } else {
            alert("알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    } catch (error) {
        console.error("서버 오류:", error);
        alert("서버와 통신 중 오류가 발생했습니다.");
    }
}


/* 댓글 작성 */
async function writeReply() {
    const boardId = document.getElementById('boardId').value;
    const replyContent = document.getElementById('replyContent').value.trim();

    if (!replyContent) {
        alert("댓글 내용을 입력해주세요.");
        return;
    }

    const payload = {
		
        boardId: boardId,
        replyContent: replyContent
    };

    try {
        const httpResponse = await fetch("/wherehouse/boards/comments", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(payload)
        });

        if (httpResponse.status === 401) {
            alert('로그인된 사용자만 댓글을 작성할 수 있습니다.');
            return;
        }

        if (httpResponse.status === 400 || httpResponse.status === 404) {
            const contentType = httpResponse.headers.get("content-type") || "";
            if (contentType.includes("application/json")) {
                const res = await httpResponse.json();
                const messages = Object.values(res).join(' ');
                alert(messages);
            } else {
                const errorText = await httpResponse.text();
                alert(errorText);
            }
            return;
        }

        if (!httpResponse.ok) {
            alert("알 수 없는 오류가 발생했습니다." + httpResponse.status);
            return;
        }

        // 댓글 작성 성공 시 현재 페이지 새로고침
        window.location.reload();

    } catch (error) {
        console.error("요청 실패:", error);
        alert("서버와 통신 중 문제가 발생했습니다.");
    }
}
