var q = document.querySelector("#question"),
    qTip = document.querySelector("#questionTip");

q.addEventListener("mouseover",()=>{
    q.setAttribute("class" , "fa-solid fa-circle-question");
    qTip.style.display = "block";
});

q.addEventListener("mouseout",()=>{
    q.setAttribute("class" , "fa-regular fa-circle-question");
    qTip.style.display = "none";
});