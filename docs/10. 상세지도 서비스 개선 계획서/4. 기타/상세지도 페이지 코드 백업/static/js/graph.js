function moveGraph(e, value) {
    e.style.width = value + "%";
    
    if (Math.round(parseFloat(e.style.width)) < 30) {
        e.style.backgroundColor = '#1c498d';
    } else if (Math.round(parseFloat(e.style.width)) >= 70) {
        e.style.backgroundColor = '#6da7ff';
    } else {
        e.style.backgroundColor = '#4082e6';
    }
}

export { moveGraph }