var mapContainer = document.getElementById('map');
var mapOption = {
        center: new kakao.maps.LatLng(37.5663, 126.9779),
        level: 3
};

var map = new kakao.maps.Map(mapContainer, mapOption);