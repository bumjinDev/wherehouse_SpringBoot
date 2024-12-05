/**
 * [이재서] 안전성 점수
 */
function saftyScore(callbacks, latlng, finalCallback) {
    const results = [];
    let count = 0;
  
    callbacks.forEach((callback, index) => {
      callback(latlng, (result) => {
        results[index] = result;
        count++;
  
        if (count === callbacks.length) {
          finalCallback(results);
        }
      });
    });
    // // R 데이터 분석을 통한 검거율 상관분석 회귀식
    // var arrest_rate = 0.8104 - 3374*Math.pow(10,-7)*population + 0.006977*poCount - 6682*Math.pow(10,-6)*density;

    // // 각각의 가중치
    // // 거리 d, cctv수 c, 검거율 r

    // // 파출소의 거리와 반경 안 cctv대수, 검거율에 가중치를 부여해 안전성 점수 산출
    // var value = distance*weight.d + cctvScore*weight.c + arrest_rate*weight.r;
    // colorChange(safty, value);
    
    // totalScore();
    // console.log("{ " + "\"거리\"\t:\t" + distance + ",\n  \"CCVT수\"\t:\t" + cctvScore + ",\n  \"검거율\"\t:\t" + arrest_rate + " }");
}

export {saftyScore}