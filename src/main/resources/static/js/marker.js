/**
 * [이재서] 핀 포인트 마커 생성
 */
var marker = new kakao.maps.Marker({ 
    // 지도 중심좌표에 마커를 생성
    position: map.getCenter(),
    zIndex: 3
});

function marker_toMouseEvent(latlng) {
    marker.setMap(map);
    marker.setPosition(latlng);
}

export {marker_toMouseEvent, marker}