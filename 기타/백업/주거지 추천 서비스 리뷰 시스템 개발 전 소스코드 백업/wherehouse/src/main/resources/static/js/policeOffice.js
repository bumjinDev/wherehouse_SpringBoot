var policeMarker = null;

// 페이지 로드 시 모든 파출소 마커 표시
fetch("/wherehouse/api/police-offices", {
    method: "GET",
    headers: {
        "Content-Type": "application/json",
    },
})
    .then(response => {
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(result => {
        console.log(`파출소 데이터 로드 성공: ${result.length}개`);

        for (var policeOffice of result) {
            var imageSrc = "./images/police_office_icon.png";
            var imageSize = new kakao.maps.Size(52, 52);
            var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);

            var marker = new kakao.maps.Marker({
                map: map,
                position: new kakao.maps.LatLng(policeOffice.latitude, policeOffice.longitude),
                title: policeOffice.address,
                image: markerImage,
                opacity: 0.9,
                zIndex: 2
            });
        }
    })
    .catch(error => {
        console.error('파출소 데이터 로드 실패:', error);
    });

function displayPoliceMarker(policeData) {
    // 기존 마커 제거
    if (policeMarker) {
        policeMarker.setMap(null);
    }

    if (policeData) {
        var imageSrc = "./images/police_office_icon.png";
        var imageSize = new kakao.maps.Size(52, 52);
        var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);

        policeMarker = new kakao.maps.Marker({
            map: map,
            position: new kakao.maps.LatLng(policeData.latitude, policeData.longitude),
            title: policeData.address,
            image: markerImage,
            opacity: 0.9,
            zIndex: 2
        });
    }
}