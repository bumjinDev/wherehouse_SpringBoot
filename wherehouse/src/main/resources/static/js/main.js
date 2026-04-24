var menu_home;
var menu_suggest_icon;
var menu_gu_icon;
var menu_detail_icon;
var menu_board_icon;
var menu_review_icon;
var menu_property_icon;  // 매물 게시판 아이콘 추가
var iframeSection;

window.onload = function () {
    menu_home = document.getElementById("menu_home");
    menu_suggest_icon = document.getElementById("menu_suggest_icon");
    menu_gu_icon = document.getElementById("menu_gu_icon");
    menu_detail_icon = document.getElementById("menu_detail_icon");
    menu_board_icon = document.getElementById("menu_board_icon");
    menu_review_icon = document.getElementById("menu_review_icon");
    menu_property_icon = document.getElementById("menu_property_icon");  // 매물 게시판 아이콘
    iframeSection = document.getElementById("iframe_section");

    initIframe();

    menu_suggest_icon.addEventListener("click", () => clickMenu(1));
    menu_gu_icon.addEventListener("click", () => clickMenu(2));
    menu_detail_icon.addEventListener("click", () => clickMenu(3));
    menu_board_icon.addEventListener("click", () => clickMenu(4));
    menu_review_icon.addEventListener("click", () => clickMenu(5));
    menu_property_icon.addEventListener("click", () => clickMenu(6));  // 매물 게시판 메뉴

    function clickMenu(sel) {
        menu_suggest_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_gu_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_detail_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_board_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_review_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
        menu_property_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";  // 매물 게시판 초기화

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
            iframeSection.src = "/wherehouse/boards/page/0";
        } else if (sel === 5) {
            menu_review_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/reviews/board";
        } else if (sel === 6) {
            menu_property_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
            iframeSection.src = "/wherehouse/properties/board";			// 매물 게시판 (신규)
        }

        console.log("sel : " + sel);
        console.log("iframeSection.src : " + iframeSection.src);
    }
}

function initIframe() {
    var iframe_target = JSON.parse(localStorage.getItem("target"));

    console.log("target : ");
    console.log(iframe_target);

    menu_gu_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_detail_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_suggest_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_board_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_review_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";
    menu_property_icon.style.backgroundColor = "rgba(11, 94, 215, 1)";  // 매물 게시판 초기화

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
        iframe_target = "/wherehouse/boards/page/0"
    } else if (iframe_target === "review_board") {
        menu_review_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/reviews/board"
    } else if (iframe_target === "property_board") {
        menu_property_icon.style.backgroundColor = "rgba(34, 34, 34, 0.3)";
        iframe_target = "/wherehouse/properties/board"		// 매물 게시판
    }

    iframeSection.src = iframe_target;
    console.log(iframe_target);
}