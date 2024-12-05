var ps = new kakao.maps.services.Places(map),
    tip = document.querySelector(".tip");


var categoryData = {
    'SW8': { data: [], weight: 10, minCount: 1 },
    'CS2': { data: [], weight: 10 },
    'FD6': { data: [], weight: 10 },
    'CE7': { data: [], weight: 10 },
    'MT1': { data: [], weight: 10, minCount: 2 },
    'BK9': { data: [], weight: 10, minCount: 2 },
    'PO3': { data: [], weight: 6, minCount: 1 },
    'CT1': { data: [], weight: 4, minCount: 3 },
    'HP8': { data: [], weight: 4, minCount: 1 },
    'PM9': { data: [], weight: 4, minCount: 4 },
    'PK6': { data: [], weight: 4, minCount: 3 },
    'OL7': { data: [], weight: 4, minCount: 2 },
    'SC4': { data: [], weight: 4, minCount: 1 },
    'AC5': { data: [], weight: 4, minCount: 3 },
    'AT4': { data: [], weight: 4, minCount: 1 },
    'AD5': { data: [], weight: 2, minCount: 1 },
};

var emarkers = [],
    dataSet,
    now,
    score;

function amenity_toMouseEvent(latlng, callback){
    dataSet = [];

    var searchOption = {
        x: latlng.La,
        y: latlng.Ma,
        radius: 500
    };

    now = 0;
    score = 0;
    removeMarkers();
    
    var promises = [];
    for (var key in categoryData) {
        promises.push(placesSearch(key, searchOption));
    }

    Promise.all(promises).then(function(results) {
        dataSet = results;
        dataSet.sort(function(a, b)  {
            var aLength = a.length || 0;
            var bLength = b.length || 0;

            return bLength - aLength;
        });
        forwardSort(dataSet,"지하철역");
        
        displayMenu(dataSet);
        callback(score);

    });

}

function placesSearch(category, searchOption) {
    return new Promise(function(resolve) {
        ps.categorySearch(category, function(data, status) {
            if (status === kakao.maps.services.Status.OK) {

                data.sort(function(a, b)  {       
                    return a.distance - b.distance;
                });

                categoryData[category].data = data;
                score += (parseInt(data.length) / 15) * categoryData[category].weight;

                if (categoryData[category].minCount && data.length > categoryData[category].minCount) {
                    score += categoryData[category].minCount;
                }

                resolve(data);
            } else {
                resolve(0);
            }
        }, searchOption);
    });
}

document.querySelectorAll(".each-menu").forEach(function(menu, index) {
    menu.onclick = function() {
        if (now === index + 1) {
            removeMarkers();
            tip.innerHTML = "*반경 500m 범위의 정보 입니다.";
            return now = 0;
        }
        clickMenu(index);
        now = index + 1;
    };
});

function displayMenu(arr) {
    document.querySelectorAll(".each-menu").forEach(function(menu, index) {
        if (arr[index] == 0)
            menu.innerHTML = "-";
        else
            menu.innerHTML = arr[index][0].category_group_name;
    });
}

function displayMarker(place) {
    var imageSrc = "./images/amenity_icon.png";
    var imageSize = new kakao.maps.Size(18, 26);
    var markerImage = new kakao.maps.MarkerImage(imageSrc, imageSize);
    var marker = new kakao.maps.Marker({
        map: map,
        position: new kakao.maps.LatLng(place.y, place.x),
        title: place.place_name,
        image: markerImage,
        zIndex: 1
    });
    emarkers.push(marker);
}

function removeMarkers() {
    for (var i = 0; i < emarkers.length; i++) {
        emarkers[i].setMap(null);
    } 
    emarkers = [];
}

function clickMenu(each) {
    removeMarkers();

    for (var i in dataSet[each]) {
        displayMarker(dataSet[each][i]);
    }

    tip.innerHTML = "*가장 가까운 " + dataSet[each][0].category_group_name + "까지 " + dataSet[each][0].distance + "m 입니다.";
}

function forwardSort(arr, name) {
    for (var i in arr) {
        if (arr[i] == 0) continue;

        if (arr[i][0].category_group_name === name) {
            var temp = arr[i];
            for (var j = 0; j < i; j++) {
                arr[i-j] = arr[i-j-1];
            }
            arr[0] = temp;
            return;
        }
    }
}

export { amenity_toMouseEvent }