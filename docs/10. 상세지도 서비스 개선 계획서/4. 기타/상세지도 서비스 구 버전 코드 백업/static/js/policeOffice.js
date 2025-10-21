/**
 * [이재서] 파출소 호출
 */
fetch("./information/policeOffice", {
    method: "get",
    headers: {
        "Content-Type": "application/json",
    },
})
    .then(response => response.json())
    .then(result => {
        for (var policeOffice of result) {
            // 마커 이미지의 이미지 주소
            var imageSrc = "./images/police_office_icon.png";
            // 마커 이미지의 이미지 크기
            var imageSize = new kakao.maps.Size(52, 52);
            // 마커 이미지를 생성
            var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);
            // 마커를 생성
            var marker = new kakao.maps.Marker({
                map: map, // 마커를 표시할 지도
                position: new kakao.maps.LatLng(policeOffice.latitude, policeOffice.longitude), // 마커를 표시할 위치
                title: policeOffice.address, // 마커의 타이틀, 마커에 마우스를 올리면 타이틀이 표시
                image: markerImage, // 마커 이미지
                opacity: 0.9, // 마커 투명도
                zIndex: 2
            });
        }
    });

function getLength_toMouseEvent(latlng, callback) {
	fetch("./information/dist?latitude=" + latlng.Ma + "&longitude=" + latlng.La, {
		method: "get",
		headers: {
			"Content-Type": "application/json",
		},
	})
		.then(response => response.json())
		.then(result => {
			var polyline = new kakao.maps.Polyline({
				path: [
					new kakao.maps.LatLng(result.latitude, result.longitude),
					new kakao.maps.LatLng(latlng.Ma, latlng.La)
				],
				strokeWeight: 0,
				strokeColor: '#fff',
				strokeOpacity: 0,
				strokeStyle: 'solid'
			});
	
			callback(polyline.getLength());
		});	
}

export {getLength_toMouseEvent}