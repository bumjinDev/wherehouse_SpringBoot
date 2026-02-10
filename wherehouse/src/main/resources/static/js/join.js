window.onload = function () {

    document.getElementById("joinBtn").addEventListener("click", async function () {
        const id = $('input[id="id"]').val();
        const pw = $('input[id="pw"]').val();
        const pwCheck = $('input[id="pw_check"]').val();
        const nickName = $('input[id="nickname"]').val();
        const tel = $('input[id="tel"]').val();
        const email = $('input[id="email"]').val();

        if (!id) return alert("아이디를 입력하세요");
        if (!pw) return alert("비밀번호를 입력하세요");
        if (pw !== pwCheck) return alert("비밀번호가 일치하지 않습니다.");
        if (!nickName) return alert("닉네임을 입력하세요");
        if (!tel) return alert("전화번호를 입력하세요");
        if (!email) return alert("이메일을 입력하세요");

        try {
            const response = await fetch("/wherehouse/members/join", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    id: id,
                    pw: pw,
                    nick_name: nickName,
                    tel: tel,
                    email: email
                })
            });

            if (response.ok) {
                alert("회원가입이 정상적으로 되었습니다.");
                window.location.href = "/wherehouse/members/login";

            } else if (response.status === 400) {
                /**
                 * @Valid 검증 실패 (MethodArgumentNotValidException)
                 *
                 * 서버 응답 구조: { "필드명": "에러메시지", ... }
                 * 예: { "id": "아이디는 필수 입력입니다", "pw": "비밀번호는 필수 입력입니다" }
                 *
                 * 각 필드별 에러 메시지를 줄바꿈으로 연결하여 alert 표시 후,
                 * 비밀번호 필드만 초기화 (재입력 유도)
                 */
                const errors = await response.json();
                const messages = Object.values(errors).join('\n');
                alert(messages);

                document.getElementById("pw").value = '';
                document.getElementById("pw_check").value = '';

            } else if (response.status === 422) {
                /**
                 * 예약어 사용 시도 (ReservedUserIdException / ReservedNicknameException)
                 *
                 * 서버 응답 구조: { "code": 422, "status": "Unprocessable Entity", "message": "..." }
                 * "admin", "root" 등 정책상 사용 불가능한 ID 또는 닉네임으로 가입 시도 시 발생.
                 * 메시지 내용에 따라 해당 필드를 선택적으로 초기화하여 재입력 유도.
                 */
                const error = await response.json();
                alert(error.message);

                if (error.message.includes("아이디")) {
                    document.getElementById("id").value = '';
                } else if (error.message.includes("닉네임")) {
                    document.getElementById("nickname").value = '';
                }

                document.getElementById("pw").value = '';
                document.getElementById("pw_check").value = '';

            } else if (response.status === 409) {
                /**
                 * 중복 충돌 (UserIdAlreadyExistsException / NicknameAlreadyExistsException)
                 *
                 * 서버 응답 구조: { "code": 409, "status": "Conflict", "message": "..." }
                 * buildResponse()에서 생성된 공통 에러 포맷
                 *
                 * message 필드만 추출하여 사용자에게 표시하고,
                 * 메시지 내용에 따라 해당 필드만 선택적으로 초기화
                 */
                const error = await response.json();
                alert(error.message);

                if (error.message.includes("아이디")) {
                    document.getElementById("id").value = '';
                } else if (error.message.includes("닉네임")) {
                    document.getElementById("nickname").value = '';
                }

                document.getElementById("pw").value = '';
                document.getElementById("pw_check").value = '';

            } else if (response.status === 401 || response.status === 404) {
                /**
                 * 401: JWT 키 없음 (JwtKeyNotFoundException)
                 * 404: 회원 미존재 (MemberNotFoundException)
                 *
                 * 회원가입 흐름에서는 정상적으로 발생하지 않는 상태 코드이나,
                 * 방어적으로 처리하여 서버 메시지를 그대로 표시
                 */
                const error = await response.json();
                alert(error.message || "요청 처리 중 오류가 발생했습니다.");

            } else {
                alert("알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }

        } catch (error) {
            console.error("서버 오류:", error);
            alert("서버와 통신 중 오류가 발생했습니다.");
        }
    });
};
