var info = document.querySelector("#information");
var func = document.querySelector("#btn");

func.addEventListener("click", panelFunc);

function panelFunc() {
    if(info.style.left === "-333px"){
        info.style.left = "0px";
        func.innerText = "◀";
    } else{
        info.style.left = "-333px";
        func.innerText = "▶";
    }
}