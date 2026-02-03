var menu_home;
var menu_suggest_icon;
var menu_gu_icon;
var menu_detail_icon;
var menu_board_icon;
var menu_review_icon;  // 리뷰 게시판 아이콘 추가
var iframeSection;

window.onload = function () {
    menu_home = document.getElementById("menu_home");
    menu_suggest_icon = document.getElementById("menu_suggest_icon");
    menu_gu_icon = document.getElementById("menu_gu_icon");
    menu_detail_icon = document.getElementById("menu_detail_icon");
    menu_board_icon = document.getElementById("menu_board_icon");
    menu_review_icon = document.getElementById("menu_review_icon");  // 리뷰 게시판 아이콘
    iframeSection = document.getElementById("iframe_section");      // 메뉴 바 제외한 본문 화면.
    // iframe 초기 값 설정
    initIframe();

    /*
    menu_home.addEventListener("click", function () {
        window.open("loginSuccess.jsp", "_parent");
    })
    */

    menu_suggest_icon.addEventListener("click", () => clickMenu(1));
    menu_gu_icon.addEventListener("click", () => clickMenu(2));
    menu_detail_icon.addEventListener("click", () => clickMenu(3));
    menu_board_icon.addEventListener("click", () => clickMenu(4));
    menu_review_icon.addEventListener("click", () => clickMenu(5));  // 리뷰 게시판 메뉴

    // 각 아이콘 클릭 시 화면 전환, 순서대로 거주지 추천, 지역구 지도, 상세 지도, 게시판, 리뷰 게시판을 클릭하여 스프링에 요청
    function clickMenu(sel) {
        menu_suggest_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_gu_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_detail_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_board_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_review_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";  // 리뷰 아이콘 초기화

        /* iframeSection.src 변경 : src 속성이 변경되면 브라우저가 해당 요청을 서버에게 전달하고 스프링에게 전달되어 요청을 처리한다. */
        if (sel === 1) {
            menu_suggest_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/houserec";
        } else if (sel === 2) {
            menu_gu_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/gumap";
        } else if (sel === 3) {
            menu_detail_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/information";
        } else if (sel === 4) {
            menu_board_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/boards/page/0";			// 기존 게시판
        } else if (sel === 5) {
            menu_review_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/reviews/board";			// 리뷰 게시판 (신규)
        }

        console.log("sel : " + sel);
        console.log("iframeSection.src : " + iframeSection.src);
    }


}

function initIframe() {
    // 로컬스토리지에 있는 값 확인 후 iframe화면 띄워주기
    var iframe_target = JSON.parse(localStorage.getItem("target"));

    console.log("target : ");
    console.log(iframe_target);

    menu_gu_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_detail_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_suggest_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_board_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_review_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";  // 리뷰 아이콘 초기화

    if (iframe_target === "house_rec") {
        menu_suggest_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/houserec";
    } else if (iframe_target === "gu_map") {
        menu_gu_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/gumap";
    } else if (iframe_target === "detail_map") {
        menu_detail_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/information";
    } else if (iframe_target === "list") {
        menu_board_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/boards/page/0"		// 기존 게시판
    } else if (iframe_target === "review_board") {
        menu_review_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/reviews/board"		// 리뷰 게시판 메인 페이지 ui 요청 (신규)
    }

    iframeSection.src = iframe_target; 				// 스프링 수정, iframeSection.src = iframe_target + ".jsp";
    console.log(iframe_target);
}