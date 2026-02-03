var marker = new kakao.maps.Marker({
    zIndex: 3
});

function marker_toMouseEvent(latlng) {
    marker.setMap(map);
    marker.setPosition(latlng);
}