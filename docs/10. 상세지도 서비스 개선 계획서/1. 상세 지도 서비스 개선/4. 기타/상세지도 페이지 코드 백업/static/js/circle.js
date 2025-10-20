/**
 * [이재서] 서클 생성
 */
var circle = new kakao.maps.Circle({
    position: map.getCenter() ,  // 원의 중심좌표
    radius: 500, // 미터 단위의 원의 반지름
    strokeWeight: 3, // 선의 두께
    strokeColor: '#0B5ED7', // 선의 색깔
    strokeOpacity: 0.7, // 선의 불투명도
    strokeStyle: 'dashed', // 선의 스타일
    fillColor: '#0B5ED7', // 채우기 색깔
    fillOpacity: 0.1,  // 채우기 불투명도
    zIndex : 1
});

function circle_toMouseEvent(latlng) {
    circle.setMap(map);
    circle.setPosition(latlng);
}

export {circle_toMouseEvent, circle}