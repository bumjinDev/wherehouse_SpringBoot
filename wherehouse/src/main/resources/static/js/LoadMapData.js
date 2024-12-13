

console.log('데이터 로드 시작!');
	
fetch('./json/mapData.json') // JSON 데이터 요청
	.then((response) => {
	  if (!response.ok) {
	    throw new Error(`HTTP error! status: ${response.status}`);
	  }
	  return response.json(); // JSON 데이터를 파싱
	})
	.then((geoData) => {
	  // "type": "FeatureCollection"을 제외하고 "features"만 추출
	  mapData = geoData.features;
	})
	.catch((error) => {
	  console.error('Error fetching GeoJSON:', error);
	});