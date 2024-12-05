var mapData = []; // 최종 데이터를 저장할 배열

console.log('데이터 로드 시작!');
	
fetch('./json/mapData.json') // JSON 데이터 요청
	.then((response) => {
	  if (!response.ok) {
		console.log('크아악 reposen 반환 실패!');
	    throw new Error(`HTTP error! status: ${response.status}`);
	  }
	  
	  return response.json(); // JSON 데이터를 파싱
	})
	.then((geoData) => {
	  console.log('으아악 성공!');
	  // "type": "FeatureCollection"을 제외하고 "features"만 추출
	  mapData = geoData.features;
	
	  // 변환된 데이터를 출력
	  console.log(mapData);
	})
	.catch((error) => {
	  console.error('Error fetching GeoJSON:', error);
	});