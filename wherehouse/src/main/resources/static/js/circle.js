var circle = new kakao.maps.Circle({
    radius: 500,
    strokeWeight: 3,
    strokeColor: '#0B5ED7',
    strokeOpacity: 0.7,
    strokeStyle: 'dashed',
    fillColor: '#0B5ED7',
    fillOpacity: 0.1,
    zIndex: 1
});

function circle_toMouseEvent(latlng) {
    circle.setMap(map);
    circle.setPosition(latlng);
}

function hideCircle() {
    circle.setMap(null);
}