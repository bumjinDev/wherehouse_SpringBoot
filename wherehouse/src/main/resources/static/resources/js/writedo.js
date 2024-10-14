function writedo() {

    var title = document.querySelector(".title").value;
    var bcontent = document.querySelector(".bcontent").value;

    if (title == '') {
        alert("제목을 입력 해주세요.");
        return;
    }
    if (bcontent == '') {
        alert("내용을 입력 해주세요.");
        return;
    }
    document.writefrm.submit();
}