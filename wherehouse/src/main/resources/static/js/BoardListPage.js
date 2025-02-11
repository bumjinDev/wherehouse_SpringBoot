var paginationbtns;         // 페이지 네이션 할 때 필요한 게시판 페이지 선택 버튼.

/* 각 버튼에 일괄적으로 클릭하면 각 버튼 번호를 해당 페이지 번호로 사용 하여 요청 하는 이벤트 등록. */
window.onload = function () {
    var paginationbtns = document.querySelectorAll('.paginationbtn button');
    for (var i = 0; i < paginationbtns.length; i++) {
        (function(i) {
            paginationbtns[i].addEventListener('click', function () {
                window.location.href = `/wherehouse/list/${i}`;
                console.log("for문 i 값 : " + i);
                let n = i + 1;
            });
        })(i);
    }
	
	document.getElementById('writebtn').addEventListener('click', function() {
		
		window.location.href = '/wherehouse/writepage'
	});
}	