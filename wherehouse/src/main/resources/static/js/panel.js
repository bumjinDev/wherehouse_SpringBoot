/**
 * [이재서] 패널 기능
 */
// ##이재서
var info = document.querySelector("#information");
var func = document.querySelector("#btn");

func.addEventListener("click", panelFunc);
kakao.maps.event.addListener(map, 'click', function() {
    if(info.style.left === "-333px"){
        info.style.left = "0px";
        func.innerText = "◀";
    }
})

function panelFunc() {
    if(info.style.left === "-333px"){
        info.style.left = "0px";
        func.innerText = "◀";
    } else{
        info.style.left = "-333px";
        func.innerText = "▶";
    }
}