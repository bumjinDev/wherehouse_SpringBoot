/**
 * [이재서] 폴리곤 기능
 */
var selectedArea;
var locate = JSON.parse(JSON.stringify(mapData));
var feat = locate.features;
var areas = {};
feat.forEach(element => {
    var geo = element.geometry;
    var coor = geo.coordinates;
    var name = element.properties.SIG_KOR_NM;
    var path = [];
    coor[0].forEach(point => {
        path.push(new kakao.maps.LatLng(point[1], point[0]));
    });
    areas[name] = path;
});

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