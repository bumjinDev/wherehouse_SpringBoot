var cctvMarkers = [];

function displayCCTVMarkers(cctvList) {
    // 기존 마커 제거
    for (var i = 0; i < cctvMarkers.length; i++) {
        cctvMarkers[i].setMap(null);
    }
    cctvMarkers = [];

    // 새 마커 생성
    cctvList.forEach(function(cctv) {
        var imageSrc = "./images/cctv_icon.png";
        var imageSize = new kakao.maps.Size(28, 28);
        var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);

        var marker = new kakao.maps.Marker({
            map: map,
            position: new kakao.maps.LatLng(cctv.latitude, cctv.longitude),
            title: '설치된 CCTV 수 : ' + cctv.camera_count,
            image: markerImage,
            opacity: 0.8,
            clickable: false,
            zIndex: 1
        });

        cctvMarkers.push(marker);
    });
}