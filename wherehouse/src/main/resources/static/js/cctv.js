/**
 * [이재서] CCTV
 */
var markers = [];

function getCCTV_toMouseEvent(latlng, callback) {
    //배열 안 마커들을 지우고
    for (var i = 0; i < markers.length; i++) {
        markers[i].setMap(null);
    }
    //배열 초기화
    markers = [];
    var cctvCount = 0;

	fetch("./information/cctv?latitude=" + latlng.Ma + "&longitude=" + latlng.La, {
        method: "get",
        headers: {
            "Content-Type": "application/json",
        },
    })
        .then(response => response.json())
        .then(result => {
            for (var cctv of result) {
                // 마커 이미지의 이미지 주소
                var imageSrc = "./images/cctv_icon.png";
                // 마커 이미지의 이미지 크기
                var imageSize = new kakao.maps.Size(28, 28);
                // 마커 이미지를 생성
                var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);
    
                var marker = new kakao.maps.Marker({
                    map: map, // 마커를 표시할 지도
                    position: new kakao.maps.LatLng(cctv.latitude, cctv.longitude), // 마커를 표시할 위치
                    title: '설치된 CCTV 수 : ' + cctv.cameraCount, // 마커의 타이틀, 마커에 마우스를 올리면 타이틀이 표시
                    image: markerImage, // 마커 이미지 
                    opacity: 0.8, // 마커 투명도
                    clickable: false, // 마커 클릭 가능 여부
                    zIndex: 1
                });
                cctvCount += parseInt(cctv.cameraCount);
                markers.push(marker);
            }
            callback(cctvCount);
        });
}

export {getCCTV_toMouseEvent}