window.onload = function () {

    document.getElementById('memberEditBtn').addEventListener("click", async function () {

        var id = $('input[id="id"]').val();
        var pw = $('input[id="pw"]').val();
        var pwcheck = $('input[id="pwcheck"]').val();
        var nickName = $('input[id="nickname"]').val();
        var tel = $('input[id="tel"]').val();
        var email = $('input[id="email"]').val();

        if (!pw) return alert('비밀번호를 입력하세요!');
        if (!pwcheck) return alert('비밀번호 확인 정보를 입력하세요!');
        if (pw !== pwcheck) return alert('비밀번호와 비밀번호 확인 내용이 다릅니다!');
        if (!nickName) return alert('닉네임을 입력하세요!');
        if (!tel) return alert('전화번호를 입력하세요!');
        if (!email) return alert('이메일을 입력하세요!');

        try {
            const response = await fetch("/wherehouse/members/edit", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ id, pw, nickName, tel, email })
            });

            if (response.ok) {
				
                alert("회원 정보 수정이 정상적으로 되었습니다.");
                window.location.href = "/wherehouse/members/loginSuccess";
				
            } else if (response.status >= 400 && response.status <= 499) {	/* 서버 측에서 요청 정보를 검증하여 아이디 누락, 핸드폰 번호 형식에 맞게 미 입력 등을 검증하여 에러를 발생 시킨 후 이에 대한 alert 을 띄운다.(400 번으로 통일) */
				
                const res = await response.json();
                const messages = Object.values(res).join('\n');
				
                alert("[입력 오류]\n" + messages);
				
                //window.location.href = "/wherehouse/members/edit?editid=" + id;
				/* 재 입력 해야 되니 리다이렉트 대신 공백 값으로 치환 */
				document.getElementById("pw").value = '';
				document.getElementById("pwcheck").value = '';
				document.getElementById("nickname").value = '';
				document.getElementById("tel").value = '';
				document.getElementById("email").value = '';
				
            } else {
                alert("알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }

        } catch (error) {
            console.error("서버 오류:", error);
            alert("서버와 통신 중 오류가 발생했습니다.");
        }
    });
}
