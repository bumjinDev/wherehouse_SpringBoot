window.onload = function () {


    /* 회원정보 수정 : modify.jsp 파일에서 회원 가입 정보 입력 시 작업. */
    document.getElementById('memberEditBtn').addEventListener("click", function () {

        /* 유효성 검증 : 사용자가 입력한 정보가 올바른지 확인. */

        var pw = $('input[id="pw"]').val();  // 비밀번호
        var pwcheck = $('input[id="pwcheck"]').val();  // 비밀번호 확인.
        var nickname = $('input[id="nickname"]').val();  // 닉네임.
        var tel = $('input[id="tel"]').val();  // 전화번호
        var email = $('input[id="email"]').val();  // 이메일

        if (pw == '') {
            alert('비밀번호를 입력하세요!');
            return;
        } else if (pwcheck == '') {
            alert('비밀번호 확인 정보를 입력하세요!');
            console.log('pwcheck');
            console.log(pwcheck);
            return;
        } else if (pw != pwcheck) {
            alert('비밀번호와 비밀번호 확인 내용이 다릅니다!');
            console.log('pw');
            console.log(pw);

            console.log('pwcheck');
            console.log(pwcheck);

            return;

        } else if (nickname == '') {
            alert('닉네임을 입력하세요!');
            return;
        } else if (tel == '') {
            alert('전화번호를 입력하세요!');
            return;
        } else if (email == '') {
            alert('이메일을 입력하세요!');
            return;
        }

        /* 유효성 검증 이상 없을 시 사용자 입력 정보를 바탕으로 회원 정보 수정 */
        console.log('회원정보 수정');
        document.getElementById('reg_frm').submit();    // 회원정보 수정 요청.
    });
}
