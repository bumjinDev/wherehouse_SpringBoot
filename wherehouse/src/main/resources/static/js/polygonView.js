/**
 * [이재서] 폴리곤 기능
 */
var selectedArea;
var areas = {};
var mapData = [];

/*
var locate = JSON.parse(JSON.stringify(mapData));
var feat = locate.features;
*/
window.onload = async function () {

    try {
        // 1. JSON 데이터 요청 및 로드
        const response = await fetch('./json/mapData.json');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const geoData = await response.json();
        mapData = geoData.features; // "features" 추출
        console.log("JSON 데이터 로드 완료:", mapData);
    } catch (error) {
        console.error('Error fetching GeoJSON:', error);
        return; // 에러 발생 시 초기화 작업 중단
    }
	
	mapData.forEach(element => {
	    var geo = element.geometry;
	    var coor = geo.coordinates;
	    var name = element.properties.SIG_KOR_NM;
	    var path = [];
	    coor[0].forEach(point => {
	        path.push(new kakao.maps.LatLng(point[1], point[0]));
	    });
	    areas[name] = path;
	});
}

function displayArea(area) {
    if (selectedArea) {selectedArea.setMap(null)}

    selectedArea = new kakao.maps.Polygon({
        map: map,
        path: areas[area],
        strokeWeight: 2,
        strokeOpacity: 0.2,
        fillOpacity: 0.1
    });
}

export { displayArea, selectedArea }