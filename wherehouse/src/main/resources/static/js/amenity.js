var amenityMarkers = [];
var currentAmenityData = null;
var selectedCategoryIndex = null;

function setAmenityData(amenityDetails) {
    currentAmenityData = amenityDetails;
    displayAmenityMenu(amenityDetails);
}

function displayAmenityMenu(amenityDetails) {
    document.querySelectorAll(".each-menu").forEach(function(menu, index) {
        if (amenityDetails[index]) {
            menu.innerHTML = amenityDetails[index].category_name;
            menu.style.color = "#3a80e9";
        } else {
            menu.innerHTML = "-";
        }
    });
}

function displayAmenityMarkers(places) {
    removeAmenityMarkers();

    var imageSrc = "./images/amenity_icon.png";
    var imageSize = new kakao.maps.Size(18, 26);
    var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);

    places.forEach(function(place) {
        var marker = new kakao.maps.Marker({
            map: map,
            position: new kakao.maps.LatLng(place.latitude, place.longitude),
            title: place.name,
            image: markerImage,
            zIndex: 1
        });
        amenityMarkers.push(marker);
    });
}

function removeAmenityMarkers() {
    for (var i = 0; i < amenityMarkers.length; i++) {
        amenityMarkers[i].setMap(null);
    }
    amenityMarkers = [];
}

document.querySelectorAll(".each-menu").forEach(function(menu, index) {
    menu.onclick = function() {
        var tip = document.querySelector(".tip");

        if (!currentAmenityData || !currentAmenityData[index]) return;

        if (selectedCategoryIndex === index) {
            removeAmenityMarkers();
            tip.innerHTML = "*반경 500m 범위의 정보 입니다.";
            selectedCategoryIndex = null;
        } else {
            var amenity = currentAmenityData[index];
            displayAmenityMarkers(amenity.places);
            tip.innerHTML = "*가장 가까운 " + amenity.category_name +
                "까지 " + amenity.closest_distance + "m 입니다.";
            selectedCategoryIndex = index;
        }
    };
});