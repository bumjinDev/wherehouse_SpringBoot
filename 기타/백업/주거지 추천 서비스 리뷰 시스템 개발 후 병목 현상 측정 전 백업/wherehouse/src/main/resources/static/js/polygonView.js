var selectedArea;
var areas = {};
var mapData = [];

window.onload = async function () {
    try {
        const response = await fetch('./json/mapData.json');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const geoData = await response.json();
        mapData = geoData.features;
        console.log("JSON 데이터 로드 완료:", mapData);
    } catch (error) {
        console.error('Error fetching GeoJSON:', error);
        return;
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