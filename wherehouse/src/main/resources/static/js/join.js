window.onload = function () {

    /* 회원가입 : join.jsp 입력 정보 검증 및 요청 */
    document.getElementById("joinBtn").addEventListener("click", function () {

        var id = $('input[id="id"]');
        var pw = $('input[id="pw"]');
        var pwCheck = $('input[id="pw_check"]');
        var nickName = $('input[id="nickname"]');
        var tel = $('input[id="tel"]');
        var email = $('input[id="email"]');

        console.log("pw : ");
        console.log(pw.val());

        console.log("pwCheck : ");
        console.log(pwCheck.val());

        if (id.val() == "") {
            alert("아이디를 입력하세요");
            id.focus();
            return;
        }

        if (pw.val() == "") {
            alert("비밀번호를 입력하세요");
            pw.focus();
            return;
        }

        if (pw.val() != pwCheck.val()) {
            alert("비밀번호가 일치하지 않습니다.");
            pwCheck.focus();
            return;
        }

        if (nickName.val() == "") {
            alert("닉네임을 입력하지 않았습니다.");
            nickName.focus();
            return;
        }

        if (tel.val() == "") {
            alert("전화번호를 입력하지 않았습니다.");
            tel.focus();
            return;
        }

        if (email.val() == "") {
            alert("이메일을 입력하지 않았습니다.");
            email.focus();
            return;
        }

        document.getElementById("reg_frm").submit();
    });
}
