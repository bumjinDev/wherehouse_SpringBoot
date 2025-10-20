import { LocationAPI } from './api.module.js';  // modules 폴더 경로 제거

// 지도 클릭 이벤트
kakao.maps.event.addListener(map, 'click', async function(mouseEvent) {
    var latlng = mouseEvent.latLng;
    var level = map.getLevel();

    try {
        // 로딩 표시 (선택사항)
        document.querySelector("#addr").textContent = "분석 중...";

        // 핀 마커 표시
        marker_toMouseEvent(latlng);

        // 반경 표시
        if (level > 2) {
            circle_toMouseEvent(latlng);
        }

        // 화면 이동
        map.panTo(latlng);

        // 패널 열기
        if(document.querySelector("#information").style.left === "-333px"){
            document.querySelector("#information").style.left = "0px";
            document.querySelector("#btn").innerText = "◀";
        }

        // API 호출
        const data = await LocationAPI.analyzeLocation(
            latlng.getLat(),
            latlng.getLng(),
            500
        );

        // UI 업데이트
        updateUI(data);

    } catch (error) {
        console.error('Location analysis error:', error);
        document.querySelector("#addr").textContent = "분석 실패. 다시 시도해주세요.";
    }
});

// 줌 레벨 변경 이벤트
kakao.maps.event.addListener(map, 'zoom_changed', function() {
    var level = map.getLevel();

    if (level > 2) {
        if (marker.getMap() == map) {
            circle.setMap(map);
            circle.setPosition(marker.getPosition());
        }
    } else {
        circle.setMap(null);
    }
});

// UI 업데이트 함수
function updateUI(data) {
    // 주소 업데이트
    document.querySelector("#addr").textContent = data.address.road_address;
    document.querySelector(".detailAddr").innerHTML =
        "도로명 : " + data.address.road_address +
        "<br>지번 : " + data.address.jibun_address;

    // 안전성 정보 업데이트
    document.querySelector("#cctvPcs").textContent =
        data.safety_score.cctv_count + ' 개';
    document.querySelector("#distance").textContent =
        data.safety_score.police_distance ?
            data.safety_score.police_distance + ' M' : '- M';

    // 그래프 업데이트
    var safty = document.querySelector("#safty");
    var conv = document.querySelector("#conv");
    var total = document.querySelector("#total");

    moveGraph(safty, data.safety_score.total);
    moveGraph(conv, data.convenience_score.total);
    moveGraph(total, data.overall_score);

    // 마커 표시
    if (data.safety_score.cctv_list) {
        displayCCTVMarkers(data.safety_score.cctv_list);
    }

    if (data.safety_score.nearest_police_office) {
        displayPoliceMarker(data.safety_score.nearest_police_office);
    }

    // 편의시설 데이터 설정
    if (data.convenience_score.amenity_details) {
        setAmenityData(data.convenience_score.amenity_details);
    }
}