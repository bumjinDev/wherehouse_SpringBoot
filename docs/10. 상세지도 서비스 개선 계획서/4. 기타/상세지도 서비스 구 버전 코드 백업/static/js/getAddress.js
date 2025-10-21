// 주소 - 좌표 변환 객체
var geocoder = new kakao.maps.services.Geocoder();

// 좌표의 법정동 상세 주소 정보 요청 함수
function searchDetailAddrFromCoords(coords, callback) {
    geocoder.coord2Address(coords.getLng(), coords.getLat(), callback);
}

// 지도의 상세 주소를 표출하는 함수
function displayDetailInfo(result) {
    document.querySelector("#addr").innerHTML = result[0].address.region_2depth_name+" "+result[0].address.region_3depth_name

    document.querySelector(".detailAddr").innerHTML = !! result[0].road_address ? "도로명 : " + result[0].road_address.address_name +
                                                        "<br>지번 : " + result[0].address.address_name
                                                        :
                                                        "도로명 : -<br>지번  : -";
}

function getAddr_toMouseEvent(latlng, callback) {
    searchDetailAddrFromCoords(latlng, (result) => {
        displayDetailInfo(result);

        callback(result[0].address.region_2depth_name);
    })
}

export {getAddr_toMouseEvent}