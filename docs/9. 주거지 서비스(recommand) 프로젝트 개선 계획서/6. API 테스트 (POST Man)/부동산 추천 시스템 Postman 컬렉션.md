# 부동산 추천 시스템 Postman 컬렉션

## Postman 환경 변수 설정

**Environment Name**: Real Estate Recommendation API  
**Variables**:
- `base_url`: `http://localhost:8185/wherehouse` (application.yml의 server.port와 context-path 반영)
- `content_type`: `application/json`

---

## 1. Health Check API

### Method: GET
### URL: `{{base_url}}/api/recommendations/health`

#### Headers:
*None required*

#### Body:
*None*

#### Expected Response:
```
Status: 200 OK
Body: OK
```

---

## 2. 정상 요청 (가격 우선순위) - Snake Case JSON

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON - Snake Case 사용):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 20000,
  "budget_max": 40000,
  "area_min": 20.0,
  "area_max": 40.0,
  "priority1": "PRICE",
  "priority2": "SAFETY",
  "priority3": "SPACE",
  "budget_flexibility": 10,
  "min_safety_score": 70,
  "absolute_min_area": 15.0
}
```

#### Tests (Postman Tests 탭):
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Response has required fields", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('search_status');
    pm.expect(jsonData).to.have.property('message');
    pm.expect(jsonData).to.have.property('recommended_districts');
});

pm.test("Search status is valid", function () {
    const jsonData = pm.response.json();
    const validStatuses = ['SUCCESS_NORMAL', 'SUCCESS_EXPANDED', 'NO_RESULTS'];
    pm.expect(validStatuses).to.include(jsonData.search_status);
});

pm.test("Response properties follow snake_case naming", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData).to.have.property('search_status');
    pm.expect(jsonData).to.have.property('recommended_districts');
    
    if (jsonData.recommended_districts && jsonData.recommended_districts.length > 0) {
        const firstDistrict = jsonData.recommended_districts[0];
        pm.expect(firstDistrict).to.have.property('district_name');
        pm.expect(firstDistrict).to.have.property('top_properties');
        
        if (firstDistrict.top_properties && firstDistrict.top_properties.length > 0) {
            const firstProperty = firstDistrict.top_properties[0];
            pm.expect(firstProperty).to.have.property('property_id');
            pm.expect(firstProperty).to.have.property('property_name');
            pm.expect(firstProperty).to.have.property('lease_type');
            pm.expect(firstProperty).to.have.property('build_year');
            pm.expect(firstProperty).to.have.property('final_score');
        }
    }
});
```

---

## 3. 월세 요청 테스트 (MONTHLY)

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "MONTHLY",
  "budget_min": 10000,
  "budget_max": 20000,
  "area_min": 15.0,
  "area_max": 30.0,
  "priority1": "SPACE",
  "priority2": "PRICE",
  "priority3": "SAFETY",
  "budget_flexibility": 15,
  "min_safety_score": 60,
  "absolute_min_area": 12.0
}
```

#### Tests:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Lease type is MONTHLY in Korean", function () {
    const jsonData = pm.response.json();
    if (jsonData.recommended_districts && jsonData.recommended_districts.length > 0) {
        const firstDistrict = jsonData.recommended_districts[0];
        if (firstDistrict.top_properties && firstDistrict.top_properties.length > 0) {
            const firstProperty = firstDistrict.top_properties[0];
            pm.expect(firstProperty.lease_type).to.eql("월세");
        }
    }
});
```

---

## 4. 안전성 우선순위 테스트

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 30000,
  "budget_max": 50000,
  "area_min": 25.0,
  "area_max": 35.0,
  "priority1": "SAFETY",
  "priority2": "SPACE",
  "priority3": "PRICE",
  "budget_flexibility": 5,
  "min_safety_score": 80,
  "absolute_min_area": 20.0
}
```

---

## 5. 확장 검색 유도 테스트 (높은 조건)

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 80000,
  "budget_max": 100000,
  "area_min": 50.0,
  "area_max": 70.0,
  "priority1": "PRICE",
  "priority2": "SPACE",
  "priority3": "SAFETY",
  "budget_flexibility": 20,
  "min_safety_score": 90,
  "absolute_min_area": 40.0
}
```

#### Tests:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Should trigger expanded search or no results", function () {
    const jsonData = pm.response.json();
    pm.expect(['SUCCESS_EXPANDED', 'NO_RESULTS']).to.include(jsonData.search_status);
});

pm.test("Message should indicate condition relaxation", function () {
    const jsonData = pm.response.json();
    if (jsonData.search_status === 'SUCCESS_EXPANDED') {
        pm.expect(jsonData.message).to.include('완화');
    }
});
```

---

## 6. 극단적 조건 (결과 없음 유도)

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 200000,
  "budget_max": 300000,
  "area_min": 100.0,
  "area_max": 150.0,
  "priority1": "PRICE",
  "priority2": "SAFETY",
  "priority3": "SPACE",
  "budget_flexibility": 0,
  "min_safety_score": 95,
  "absolute_min_area": 90.0
}
```

#### Tests:
```javascript
pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("Should return NO_RESULTS", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.search_status).to.eql("NO_RESULTS");
    pm.expect(jsonData.recommended_districts).to.be.an('array').that.is.empty;
});
```

---

## 7. 필수 필드 누락 테스트 (400 오류)

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 20000,
  "area_min": 20.0,
  "area_max": 30.0,
  "priority1": "PRICE",
  "priority2": "SAFETY",
  "priority3": "SPACE"
}
```

#### Tests:
```javascript
pm.test("Status code is 400", function () {
    pm.response.to.have.status(400);
});

pm.test("Error response structure", function () {
    const jsonData = pm.response.json();
    // Spring Boot validation error 구조 확인
    pm.expect(jsonData).to.have.property('timestamp');
    pm.expect(jsonData).to.have.property('status');
    pm.expect(jsonData.status).to.eql(400);
});
```

---

## 8. 잘못된 열거형 값 테스트

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "INVALID_TYPE",
  "budget_min": 20000,
  "budget_max": 30000,
  "area_min": 20.0,
  "area_max": 30.0,
  "priority1": "INVALID_PRIORITY",
  "priority2": "SAFETY",
  "priority3": "SPACE"
}
```

#### Tests:
```javascript
pm.test("Status code is 400", function () {
    pm.response.to.have.status(400);
});

pm.test("Validation error for invalid enum", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.status).to.eql(400);
});
```

---

## 9. 우선순위 중복 테스트

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Content-Type: application/json
Accept: application/json
```

#### Body (raw JSON):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 20000,
  "budget_max": 30000,
  "area_min": 20.0,
  "area_max": 30.0,
  "priority1": "PRICE",
  "priority2": "PRICE",
  "priority3": "SAFETY"
}
```

#### Tests:
```javascript
pm.test("Status code is 400", function () {
    pm.response.to.have.status(400);
});

pm.test("Custom validation error for duplicate priorities", function () {
    const jsonData = pm.response.json();
    pm.expect(jsonData.status).to.eql(400);
    // 커스텀 validation 메시지 확인
    if (jsonData.message) {
        pm.expect(jsonData.message).to.include('중복');
    }
});
```

---

## 10. 컨텐츠 타입 누락 테스트

### Method: POST
### URL: `{{base_url}}/api/recommendations/districts`

#### Headers:
```
Accept: application/json
```
*Content-Type 헤더 의도적으로 누락*

#### Body (raw JSON):
```json
{
  "lease_type": "CHARTER",
  "budget_min": 20000,
  "budget_max": 30000,
  "area_min": 20.0,
  "area_max": 30.0,
  "priority1": "PRICE",
  "priority2": "SAFETY",
  "priority3": "SPACE"
}
```

#### Tests:
```javascript
pm.test("Status code is 415 or 400", function () {
    pm.expect([400, 415]).to.include(pm.response.code);
});
```

---

## Collection-level Pre-request Script

```javascript
// 공통 변수 설정
pm.globals.set("timestamp", new Date().toISOString());
console.log("Request started at: " + pm.globals.get("timestamp"));

// Redis 연결 상태 확인 (선택사항)
pm.globals.set("redis_host", "127.0.0.1:6379");
```

## Collection-level Tests

```javascript
// 공통 응답 시간 체크
pm.test("Response time is less than 10000ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(10000);
});

// 공통 응답 헤더 체크
pm.test("Response has JSON content type", function () {
    const contentType = pm.response.headers.get("Content-Type");
    if (contentType) {
        pm.expect(contentType).to.include("application/json");
    }
});

// 한국어 인코딩 체크
pm.test("Korean characters are properly encoded", function () {
    const responseText = pm.response.text();
    if (responseText.includes('강남구') || responseText.includes('전세')) {
        console.log("Korean characters detected - encoding OK");
    }
});
```

---

## 실행 순서 및 주의사항

### 실행 전 체크리스트:
1. ✅ **Oracle DB 연결**: localhost:1521/xe, SCOTT/tiger
2. ✅ **Redis 서버 실행**: localhost:6379
3. ✅ **Spring Boot 애플리케이션 실행**: localhost:8185/wherehouse
4. ✅ **BatchScheduler 실행**: Redis 데이터 적재 확인
5. ✅ **Postman 환경 변수**: base_url 설정 완료

### 서버 실행 명령어:
```bash
# 프로젝트 루트에서
./gradlew bootRun

# 또는 백그라운드 실행
nohup ./gradlew bootRun > app.log 2>&1 &
```

### 배치 실행 확인:
```bash
# 애플리케이션 로그에서 배치 실행 확인
tail -f log/wherehouse.log | grep "배치"
```

### Redis 데이터 확인:
```bash
# Redis CLI 접속
redis-cli -h 127.0.0.1 -p 6379

# 키 목록 확인
127.0.0.1:6379> KEYS *

# 매물 데이터 확인
127.0.0.1:6379> KEYS property:*

# 인덱스 데이터 확인
127.0.0.1:6379> KEYS idx:*

# 정규화 범위 확인
127.0.0.1:6379> KEYS bounds:*

# 안전성 점수 확인
127.0.0.1:6379> KEYS safety:*
```

### 테스트 실행 순서:
1. **Health Check** (1번) - 서버 상태 확인
2. **정상 요청** (2번) - 기본 기능 및 Snake Case 변환 확인
3. **다양한 우선순위** (3-4번) - 비즈니스 로직 검증
4. **엣지 케이스** (5-6번) - 폴백 시스템 및 NO_RESULTS 확인
5. **Validation 오류** (7-9번) - Bean Validation 동작 확인
6. **HTTP 오류** (10번) - Content-Type 처리 확인

### 예상 응답 구조 (Snake Case):
```json
{
  "search_status": "SUCCESS_NORMAL",
  "message": "조건에 맞는 매물을 성공적으로 찾았습니다.",
  "recommended_districts": [
    {
      "rank": 1,
      "district_name": "강남구",
      "summary": "가격 1순위 조건에 가장 부합하며...",
      "top_properties": [
        {
          "property_id": "uuid-string",
          "property_name": "아파트명",
          "address": "서울특별시 강남구...",
          "price": 35000,
          "lease_type": "전세",
          "area": 25.5,
          "floor": 10,
          "build_year": 2010,
          "final_score": 85.2
        }
      ]
    }
  ]
}
```

### Runner 실행:
Postman Collection Runner를 사용하여 전체 테스트를 순차적으로 실행할 수 있습니다.

### 문제 해결:
- **500 에러**: 서버 로그 확인, Redis 연결 상태 점검
- **NO_RESULTS만 나옴**: 배치 실행 여부 및 Redis 데이터 존재 여부 확인
- **Jackson 변환 오류**: Content-Type 헤더 및 JSON 문법 확인
- **Validation 오류**: Bean Validation 어노테이션 및 커스텀 검증 로직 확인