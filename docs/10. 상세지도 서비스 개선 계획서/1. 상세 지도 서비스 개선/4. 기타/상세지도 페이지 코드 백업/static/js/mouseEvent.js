//빅데이터 분석을 위한 변수
var bigData = { distAVG : 0,
                cctvAVG : 0 },
    distAvg = 0,
    cctvAvg = 0,
    count = 0;
/**
 * [이재서] 마우스 이벤트
 */
import { displayArea, selectedArea } from "./polygonView.js";
import { marker_toMouseEvent, marker } from "./marker.js";
import { circle_toMouseEvent, circle } from "./circle.js";
import { moveGraph } from "./graph.js";
import { getLength_toMouseEvent } from "./policeOffice.js";
import { getCCTV_toMouseEvent } from "./cctv.js";
import { getAddr_toMouseEvent } from "./getAddress.js";
import { saftyScore } from "./score.js";
import { amenity_toMouseEvent } from "./amenity.js";

var saftyWeight = { d : 60,
    c : 30,
    r : 10 };

// 지도에 클릭 이벤트를 등록
// 지도를 클릭하면 마지막 파라미터로 넘어온 함수를 호출
kakao.maps.event.addListener(map, 'click', function(mouseEvent) {
    // 클릭한 위도, 경도 정보
    var latlng = mouseEvent.latLng
    var level = map.getLevel();
	
    if (level > 2) {
        //500m 서클 표시
        circle_toMouseEvent(latlng);
    }
    
    if (level > 3) {
        // 클릭한 좌표가 해당하는 자치구 표시
        viewGu(latlng)
    }

    //핀포인트 마커 표시
    marker_toMouseEvent(latlng);
    
    // 화면 이동
    map.panTo(latlng);

    var promises = [];
    //안전성 점수 시각화 그래프
    promises.push(new Promise((resolve) => {
        saftyScore([distFunction, cctvFunction, arrestRateFunction], latlng, (results) => {
            var safty = document.querySelector("#safty");
            var value = results[0]*saftyWeight.d + results[1]*saftyWeight.c + results[2]*saftyWeight.r;
            
            distAvg += results[0];
            cctvAvg += results[1];
            count++;
            console.log(count);
	
            if (count == 100) {
                bigData.distAVG = distAvg/count;
                bigData.cctvAVG = cctvAvg/count;
                console.log(bigData);
            }

            moveGraph(safty, value);
            resolve(value)
        });
    }));
    //편의성 점수 시각화 그래프
    promises.push(new Promise((resolve) => {
        amenity_toMouseEvent(latlng, (result) => {
            var conv = document.querySelector("#conv");

            moveGraph(conv, result);
            resolve(result);
        });
    }));

    //종합 점수 시각화 그래프
    Promise.all(promises).then((results) => {
        var total = document.querySelector("#total");
        var value = (results[0]+results[1])/2;

        moveGraph(total, value);
    });
});

kakao.maps.event.addListener(map, 'zoom_changed', function() {
    var level = map.getLevel();

    if (level > 2) {
        if (marker.getMap() == map)
            circle.setMap(map);
        circle.setPosition(marker.getPosition());
    }
    else {
        circle.setMap(null);
    }

    if (level > 3) {
        if (selectedArea != null)
            selectedArea.setMap(map);
    }
    else {
        if (selectedArea != null)
            selectedArea.setMap(null);
    }
});

//=======================================================================================================

function distFunction(latlng, callback) {
    //파출소 최단거리를 표시하고 거리식 적용해서 콜백
    getLength_toMouseEvent(latlng, (result) => {
        document.querySelector("#distance").innerHTML = Math.round(result) + ' M';
        var distScore;
        if (result < 1000) { distScore = (1 / Math.log(result + 150))/0.2; }
        else if (result < 1743 && result >= 1000) { distScore = (1 / Math.log(result - 850))/0.5; }
        else { distScore = (1 / Math.log(result - 1700)); }

        callback(distScore)
    })
}

function cctvFunction(latlng, callback) {
    // 반경 500m안 cctv을 표시하고 cctv가 300대 이상이면 만점. 계산된 점수 콜백
    getCCTV_toMouseEvent(latlng, (result) => {
        document.querySelector("#cctvPcs").innerHTML = result + ' 개';
        var cctvScore = Math.min(result/300, 1);

        callback(cctvScore);
    })
}

function arrestRateFunction(latlng, callback) {
    getAddr_toMouseEvent(latlng, (result) => {
        fetch("./information/rate?addr=" + result, {
            method: "get",
            headers: {
                "Content-Type": "application/json",
            },
        })
            .then(response => response.json())
            .then(result => {
                callback(result.rate);
            });
    })
}

function viewGu(latlng) {
    getAddr_toMouseEvent(latlng, (result) => {
        displayArea(result);
    })
}