# F009 Write-Read 경합 재현 테스트 계획

**문서 목적:**  
F009에서 정의한 Redis 검색 인덱스 동기화 쓰기 시퀀스의 Write-Read 경합 문제를 실제 API 요청 기반으로 재현하기 위한 테스트 계획을 정리한다.

**검증 대상:**  
매물 수정 API가 Redis Hash를 먼저 갱신하고, 가격/평수 ZSet 인덱스를 나중에 갱신하는 순차 쓰기 구조에서, 추천 조회 API가 그 중간 상태를 관찰하여 조회 조건과 모순되는 매물을 응답에 포함하는지 확인한다.

---

## 1. 문제 정의

F009의 핵심 문제는 추천 점수의 정확성 문제가 아니다.  
문제는 Reader가 Writer의 Redis 중간 상태를 관찰할 수 있다는 점이다.

특히 UPDATE 경로에서는 다음 상태가 발생할 수 있다.

```text
전제:
- 매물 A의 기존 deposit = 50000
- 매물 A의 가격 ZSet score = 50000
- 사용자 조회 조건 budgetMin=40000, budgetMax=60000

수정 요청:
- 매물 A의 deposit을 80000으로 변경

위험 시퀀스:
1. Writer가 Redis Hash를 먼저 갱신한다.
   property:charter:A Hash deposit = 80000

2. Writer가 아직 가격 ZSet을 갱신하지 못한 상태에서 정지한다.
   idx:charterPrice:강남구 ZSet score = 50000

3. Reader가 추천 조회를 수행한다.
   ZRANGEBYSCORE idx:charterPrice:강남구 40000 60000
   → A는 score=50000이므로 후보에 포함된다.

4. Reader가 후보 A의 Hash 상세를 조회한다.
   HGETALL property:charter:A
   → deposit=80000

결과:
- 조회 조건은 budgetMax=60000인데,
- 추천 응답에는 deposit=80000인 매물이 포함된다.
```

따라서 테스트의 판정 기준은 다음이다.

```text
조회 조건:
budgetMin=40000
budgetMax=60000

실패 재현 조건:
추천 조회 응답에 targetPropertyId가 포함된다.
AND 해당 매물의 deposit이 80000이다.
```

이 결과가 나오면 Reader가 ZSet 구값과 Hash 신값을 조합하여 불일치 상태를 관찰한 것이다.

---

## 2. 테스트 방식 선택

Postman 또는 JMeter만으로 요청 타이밍을 맞추는 방식은 재현성이 낮다.  
`Thread.sleep()` 역시 특정 시간이 지난 뒤 원하는 중간 상태가 보장되지 않으므로 우연성에 의존한다.

따라서 본 테스트에서는 테스트 전용 동기화 컴포넌트를 추가한다.

핵심 아이디어는 다음과 같다.

```text
Writer:
Redis Hash 갱신 직후, ZSet 갱신 전에 await()로 정지한다.

Reader:
findValidCharterPropertiesInDistrict()에서 ZSet 기반 후보 ID를 확정한 직후,
countDown()으로 Writer를 재개시킨다.
```

이렇게 하면 Reader가 반드시 다음 상태에서 후보를 확정한다.

```text
Hash = 신값
ZSet = 구값
```

이후 Writer가 ZSet을 갱신하더라도 Reader는 이미 구 ZSet 기준으로 확보한 후보 ID 목록을 가지고 있으므로, 이후 Hash 상세 조회 단계에서 신값을 읽게 된다.

---

## 3. 테스트 Redis 데이터 준비

테스트 대상 지역구는 예시로 `강남구`를 사용한다.  
실제 테스트에서는 프로젝트의 기존 데이터와 충돌하지 않는 propertyId를 사용한다.

### 3-1. 대상 매물 A

```text
propertyId = F009_TARGET_A
districtName = 강남구
leaseType = 전세
status = ACTIVE

초기 Hash:
property:charter:F009_TARGET_A
  deposit = 50000
  areaInPyeong = 30
  status = ACTIVE
  districtName = 강남구

초기 ZSet:
idx:charterPrice:강남구
  F009_TARGET_A score = 50000

idx:area:강남구:전세
  F009_TARGET_A score = 30
```

### 3-2. 추가 테스트 매물 B, C

추천 서비스는 지역구별 후보 매물 수가 3개 미만이면 fallback 경로로 들어갈 수 있다.  
따라서 테스트 대상 지역구에서 1차 strict search 결과가 유지되도록 조건에 맞는 매물을 최소 3개 이상 준비한다.

```text
propertyId = F009_DUMMY_B
deposit = 52000
areaInPyeong = 31
status = ACTIVE

propertyId = F009_DUMMY_C
deposit = 54000
areaInPyeong = 32
status = ACTIVE
```

두 매물 모두 다음 조건에 포함되도록 Redis Hash와 ZSet을 설정한다.

```text
budgetMin=40000
budgetMax=60000
areaMin=20
areaMax=40
```

즉, 테스트 지역구 `강남구`의 strict search 결과에 최소 3개 매물이 포함되어야 한다.

---

## 4. 테스트 전용 공유 컴포넌트

테스트 전용으로 Writer와 Reader가 공유하는 컴포넌트를 둔다.

```java
@Component
@Profile("f009-test")
public class F009RaceLatch {

    private static final String TARGET_PROPERTY_ID = "F009_TARGET_A";

    private CountDownLatch writerReleaseLatch = new CountDownLatch(1);

    private volatile boolean enabled = false;
    private volatile String targetPropertyId;
    private volatile boolean writerWaiting = false;

    @PostConstruct
    public void init() {
        enableFor(TARGET_PROPERTY_ID);
    }

    public synchronized void enableFor(String propertyId) {
        this.targetPropertyId = propertyId;
        this.writerReleaseLatch = new CountDownLatch(1);
        this.enabled = true;
        this.writerWaiting = false;
    }

    public void syncAwait(String propertyId) {
        if (!enabled) {
            return;
        }

        if (!propertyId.equals(targetPropertyId)) {
            return;
        }

        writerWaiting = true;

        try {
            boolean released = writerReleaseLatch.await(30, TimeUnit.SECONDS);
            if (!released) {
                throw new IllegalStateException("F009 테스트 latch 대기 시간 초과");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("F009 테스트 대기 중 인터럽트 발생", e);
        } finally {
            writerWaiting = false;
        }
    }

    public void releaseWriterIfTargetIncluded(Collection<String> propertyIds) {
        if (!enabled) {
            return;
        }

        if (targetPropertyId == null) {
            return;
        }

        if (propertyIds.contains(targetPropertyId)) {
            writerReleaseLatch.countDown();
        }
    }

    public boolean isWriterWaiting() {
        return writerWaiting;
    }

    public synchronized void disable() {
        this.enabled = false;
        this.targetPropertyId = null;
        this.writerReleaseLatch.countDown();
        this.writerWaiting = false;
    }
}
```

주의 사항:

1. 이 컴포넌트는 반드시 `f009-test` 같은 테스트 전용 profile에서만 활성화한다.
2. 운영 profile에서는 절대로 활성화하지 않는다.
3. `await()`에는 timeout을 둔다. 조회 API를 호출하지 않으면 수정 API 요청이 영원히 멈추기 때문이다.
4. `CountDownLatch`는 1회성 도구이므로 테스트 반복 전 `enableFor()`를 통해 latch를 새로 생성한다.
5. 특정 `propertyId`에 대해서만 동작하게 하여 다른 수정 요청이 멈추지 않도록 한다.

---

## 5. Writer 측 테스트 훅 위치

대상 위치는 `CharterPropertyWriteService.syncRedisAfterUpdate()`이다.

Hash 갱신 직후, 가격 ZSet 갱신 전에 `syncAwait()`를 호출한다.

```java
private void syncRedisAfterUpdate(PropertyCharterEntity entity, List<String> changedFields) {
    String propertyId = entity.getPropertyId();
    String districtName = entity.getDistrictName();

    String hashKey = "property:charter:" + propertyId;
    Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);

    redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

    // F009 테스트 훅:
    // Hash는 신값으로 갱신되었고,
    // 가격 ZSet은 아직 구값인 상태에서 Writer를 정지시킨다.
    f009RaceLatch.syncAwait(propertyId);

    if (changedFields.contains("deposit")) {
        redisHandler.redisTemplate.opsForZSet().add(
                "idx:charterPrice:" + districtName,
                propertyId,
                entity.getDeposit().doubleValue()
        );

        String boundsKey = "bounds:" + districtName + ":전세";
        boundsUpdater.tryExtend(
                boundsKey,
                "minPrice",
                "maxPrice",
                entity.getDeposit().doubleValue(),
                PRICE_ZERO_DELTA
        );
    }

    if (changedFields.contains("area")) {
        redisHandler.redisTemplate.opsForZSet().add(
                "idx:area:" + districtName + ":전세",
                propertyId,
                entity.getAreaInPyeong().doubleValue()
        );

        String boundsKey = "bounds:" + districtName + ":전세";
        boundsUpdater.tryExtend(
                boundsKey,
                "minArea",
                "maxArea",
                entity.getAreaInPyeong().doubleValue(),
                AREA_ZERO_DELTA
        );
    }

    log.info("전세 매물 Redis 동기화(수정) 완료: propertyId={}, changedFields={}",
            propertyId, changedFields);
}
```

이 훅이 동작하면 매물 수정 API 요청은 다음 상태에서 대기한다.

```text
Hash deposit = 80000
가격 ZSet score = 50000
```

---

## 6. Reader 측 테스트 훅 위치

대상 위치는 `CharterRecommendationService.findValidCharterPropertiesInDistrict()`이다.

가격 ZSet과 평수 ZSet의 교집합을 확정한 직후 `releaseWriterIfTargetIncluded()`를 호출한다.

```java
private List<String> findValidCharterPropertiesInDistrict(
        String district,
        CharterRecommendationRequestDto request
) {
    try {
        String charterPriceIndexKey = "idx:charterPrice:" + district;
        Set<Object> priceValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(charterPriceIndexKey, request.getBudgetMin(), request.getBudgetMax());

        if (priceValidObjects == null || priceValidObjects.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> priceValidIds = priceValidObjects.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        String areaIndexKey = "idx:area:" + district + ":전세";
        Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());

        if (areaValidObjects == null || areaValidObjects.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> areaValidIds = areaValidObjects.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        priceValidIds.retainAll(areaValidIds);

        // F009 테스트 훅:
        // Reader가 구 ZSet 기반 후보 ID 목록을 확정한 직후 Writer를 재개시킨다.
        f009RaceLatch.releaseWriterIfTargetIncluded(priceValidIds);

        return new ArrayList<>(priceValidIds);

    } catch (Exception e) {
        log.info("전세 매물 검색 중 오류 - 지역구: {}", district, e);
        return Collections.emptyList();
    }
}
```

이 위치에서 Writer를 풀어도 되는 이유는 다음과 같다.

```text
1. Reader는 이미 구 ZSet 기준으로 targetPropertyId를 후보 목록에 포함했다.
2. 이후 Writer가 ZSet을 신값으로 갱신해도 Reader가 들고 있는 후보 ID 목록은 바뀌지 않는다.
3. Reader는 이후 Hash 상세 조회 단계에서 신값 deposit=80000을 읽는다.
4. 따라서 ZSet 후보 선정 기준과 Hash 상세 데이터가 불일치한 상태가 응답에 반영된다.
```

---

## 7. JMeter 요청 시나리오

### 7-1. 사전 준비

본 시나리오를 실행하기 전에 다음 3가지 조건이 충족되어야 한다.

#### 1) 애플리케이션 기동

```text
spring.profiles.active=f009-test 로 기동한다.

기동 시 자동 수행:
- F009RaceLatch 빈 생성 (@Component @Profile("f009-test"))
- @PostConstruct에서 enableFor("F009_TARGET_A") 자동 호출
  → targetPropertyId 설정, enabled=true, CountDownLatch(1) 생성

기동 후 확인 사항:
1. F009RaceLatch 빈 로드 로그를 확인한다.
2. 배치 비활성화를 확인한다. (12절 참조)
   - BatchScheduler 실행 로그 없음
   - executeBatchProcess() 호출 로그 없음
   - redisHandler.clearCurrentRedisDB() 호출 로그 없음
```

별도 활성화 API 호출은 불필요하다.  
`f009-test` 프로파일 기동만으로 latch가 활성화되고, `CharterPropertyWriteService`와 `CharterRecommendationService`에 생성자 주입된다.

#### 2) 테스트 데이터 주입

```text
RDBMS:
- PROPERTIES_CHARTER 3건 직접 삽입 (13-3절 참조)
- REVIEW_STATISTICS 3건 직접 삽입 (13-4절 참조)

Redis:
- property:charter:* Hash 3건 (14-4 ~ 14-6절 참조)
- idx:charterPrice:강남구 ZSet (14-7절 참조)
- idx:area:강남구:전세 ZSet (14-8절 참조)
- bounds:강남구:전세 Hash (14-3절 참조)
- safety:강남구 Hash (14-3절 참조)
```

#### 3) 인증 토큰 확보

두 API 모두 `@AuthenticationPrincipal String userId`로 인증된 사용자를 요구한다.

```text
매물 수정 API (PropertyWriteController.updateCharterProperty):
  현재 코드에서 verifyOwnership()이 비활성화 상태이다.
  (CharterPropertyWriteService:358 — "요구사항 수정 : 모든 사용자가 등록자 외에도 접근하여 수정 가능하도록")
  따라서 인증된 사용자라면 누구나 수정 가능하다.
  테스트용 사용자 토큰이면 충분하다.

추천 조회 API (RecommendationController.getCharterDistrictRecommendations):
  userId는 TopCharterPropertyDto의 ownedByCurrentUser, canEdit, canChangeStatus 필드
  산출에만 사용되며, 후보 필터링에는 관여하지 않는다.
```

JMeter HTTP Header Manager에 다음 헤더를 설정한다.

```text
Authorization: Bearer {테스트용_JWT_토큰}
```

프로젝트 인증 방식이 세션 기반이면 로그인 API를 사전 호출하여 세션 쿠키를 확보한다.

### 7-2. JMeter Thread Group 구성

매물 수정 API(Writer)와 추천 조회 API(Reader)는 서로 다른 HTTP 요청이며, 동시에 서버에 도달해야 한다.  
Writer가 latch에서 멈춘 상태에서 Reader가 진입해야 하므로, 두 요청을 하나의 Thread Group 안에 순차 배치하면 안 된다.

```text
Test Plan
├── Thread Group 1 — Writer (매물 수정)
│   ├── HTTP Header Manager
│   │     Content-Type: application/json
│   │     Authorization: Bearer {토큰}
│   ├── HTTP Request — PATCH 매물 수정
│   └── View Results Tree
│
└── Thread Group 2 — Reader (추천 조회)
    ├── HTTP Header Manager
    │     Content-Type: application/json
    │     Authorization: Bearer {토큰}
    ├── Constant Timer — 2000ms (Writer가 latch 도달 대기)
    ├── HTTP Request — POST 추천 조회
    ├── JSON Assertion
    └── View Results Tree
```

Thread Group 설정값:

```text
Thread Group 1 — Writer:
  Number of Threads: 1
  Ramp-Up Period: 0
  Loop Count: 1
  Startup Delay: 0초

Thread Group 2 — Reader:
  Number of Threads: 1
  Ramp-Up Period: 0
  Loop Count: 1
  Startup Delay: 0초
  Constant Timer: 2000ms (첫 요청 전 대기)
```

Reader의 Constant Timer 2000ms는 Writer가 `f009RaceLatch.syncAwait()` 도달점까지 실행되기에 충분한 시간을 확보하기 위한 값이다.

```text
Writer 경로 (latch 도달까지):
  CharterPropertyWriteService.updateProperty() 진입
  → charterRepository.findById()              — RDB 조회
  → verifyActiveForUpdate(ACTIVE)             — 상태 검증
  → entity.setDeposit(80000L)                 — 엔티티 변경
  → charterRepository.save(entity)            — RDB 커밋
  → syncRedisAfterUpdate() 진입
  → opsForHash().putAll()                     — Hash 갱신
  → f009RaceLatch.syncAwait()                 — 여기서 정지
```

일반적 로컬 환경에서 이 경로는 100ms 이내에 latch 도달점까지 실행되므로, 2000ms는 충분한 마진이다.  
다만 환경에 따라 Constant Timer 값을 조정한다.

Writer 대기 상태를 보다 확실히 확인하려면 Constant Timer 대기 중에 redis-cli로 중간 상태를 수동 확인한다. (15-2절 참조)

### 7-3. 요청 1 — 매물 수정 API (Writer)

대상 매물 F009_TARGET_A의 deposit을 `50000`에서 `80000`으로 수정한다.

#### HTTP 요청 명세

```text
Method:  PATCH
URL:     http://localhost:8080/api/v1/properties/charter/F009_TARGET_A

Headers:
  Content-Type: application/json
  Authorization: Bearer {테스트용_JWT_토큰}
```

엔드포인트 근거:

```text
PropertyWriteController 클래스:
  @RequestMapping("/api/v1/properties")

updateCharterProperty 메서드:
  @PatchMapping("/charter/{propertyId}")

결합 경로: /api/v1/properties/charter/{propertyId}
```

#### 요청 본문

`CharterUpdateRequestDto` 기준이다.  
수정 가능 필드는 `deposit`(Integer), `buildYear`(Integer), `dealDate`(String) 3개뿐이다.

```json
{
  "deposit": 80000
}
```

`buildYear`, `dealDate`는 이 테스트에서 변경하지 않으므로 요청 본문에 포함하지 않는다.  
PATCH 시맨틱에 따라 요청 본문에 포함된 필드만 갱신 대상이 된다.

#### 기대 응답 (latch 해제 후)

`PropertyUpdateResponseDto` 기준:

```json
{
  "propertyId": "F009_TARGET_A",
  "modifiedAt": "2026-05-06T...",
  "changedFields": ["deposit"]
}
```

HTTP 200 OK.  
단, latch가 해제될 때까지 이 응답은 도착하지 않는다.

#### Writer 측 코드 실행 흐름

다음은 `CharterPropertyWriteService.updateProperty()` → `syncRedisAfterUpdate()` 경로의 실행 순서이다.

```text
CharterPropertyWriteService.updateProperty() 진입

1. charterRepository.findById("F009_TARGET_A")
   → entity 조회 (deposit=50000, status=ACTIVE)
   → DELETED 필터 통과

2. verifyActiveForUpdate(ACTIVE)
   → 통과
   (verifyOwnership은 현재 코드에서 비활성 — F002 요구사항 수정)

3. dto.getDeposit()=80000, entity.getDeposit()=50000
   → longEqualsInteger(50000L, 80000) = false
   → entity.setDeposit(80000L)
   → changedFields = ["deposit"]

4. entity.setModifiedAt(now)

5. charterRepository.save(entity)
   → @Transactional 비활성 상태이므로 SimpleJpaRepository.save()의
     자체 @Transactional에 의해 즉시 커밋
   → RDB: F009_TARGET_A.DEPOSIT = 80000

6. syncRedisAfterUpdate(entity, ["deposit"]) 진입

7. redisTemplate.opsForHash().putAll("property:charter:F009_TARGET_A", hashFields)
   → Redis Hash: deposit = 80000 (신값으로 갱신 완료)

8. f009RaceLatch.syncAwait("F009_TARGET_A")
   → Writer 스레드 정지
   → 이 시점의 Redis 상태:
     property:charter:F009_TARGET_A Hash deposit = 80000 (신값)
     idx:charterPrice:강남구 F009_TARGET_A score = 50000 (구값, 미갱신)

   ─── Reader가 releaseWriterIfTargetIncluded()를 호출할 때까지 대기 ───

9. [Reader가 latch 해제 — 7-4절 단계 7에서 발생]

10. changedFields.contains("deposit") = true
    → redisTemplate.opsForZSet().add(
        "idx:charterPrice:강남구", "F009_TARGET_A", 80000.0)
    → Redis: idx:charterPrice:강남구 F009_TARGET_A score = 80000 (신값)

11. boundsUpdater.tryExtend("bounds:강남구:전세", "minPrice", "maxPrice", 80000.0, 1000.0)

12. syncRedisAfterUpdate 반환 → updateProperty 반환 → HTTP 200 응답 전송
```

### 7-4. 요청 2 — 추천 조회 API (Reader)

수정 전 가격 기준(ZSet score=50000)으로 대상 매물이 후보에 포함되도록 조회 조건을 설정한다.

#### HTTP 요청 명세

```text
Method:  POST
URL:     http://localhost:8080/api/recommendations/charter-districts

Headers:
  Content-Type: application/json
  Authorization: Bearer {테스트용_JWT_토큰}
```

엔드포인트 근거:

```text
RecommendationController 클래스:
  @RequestMapping("/api/recommendations")

getCharterDistrictRecommendations 메서드:
  @PostMapping("/charter-districts")

결합 경로: /api/recommendations/charter-districts
```

#### 요청 본문

`CharterRecommendationRequestDto` 기준이다.  
전체 필드를 명시한다.

```json
{
  "budgetMin": 40000,
  "budgetMax": 60000,
  "areaMin": 20.0,
  "areaMax": 40.0,
  "priority1": "PRICE",
  "priority2": "SAFETY",
  "priority3": "SPACE",
  "budgetFlexibility": 0,
  "minSafetyScore": 0,
  "absoluteMinArea": 0.0
}
```

필드별 근거:

```text
budgetMin=40000, budgetMax=60000:
  F009_TARGET_A의 구 deposit=50000이 이 범위에 포함되어야 한다.
  수정 후 deposit=80000은 이 범위를 초과한다.

areaMin=20.0, areaMax=40.0:
  F009_TARGET_A의 areaInPyeong=30이 이 범위에 포함되어야 한다.

priority1~3:
  추천 점수 가중치 배분용. 테스트 핵심에 영향 없음.
  PRICE, SAFETY, SPACE 중복 없이 지정한다.

budgetFlexibility=0:
  예산 유연성 없음. expanded search 미발동.

minSafetyScore=0:
  안전성 점수 최소 요건 없음. 강남구 안전성 점수에 무관하게 후보 유지.

absoluteMinArea=0.0:
  절대 최소 면적 제한 없음.
```

#### 기대 응답 구조

`CharterRecommendationResponseDto` → `RecommendedCharterDistrictDto` → `TopCharterPropertyDto` 기준이다.

```json
{
  "searchStatus": "SUCCESS_NORMAL",
  "message": "...",
  "recommendedDistricts": [
    {
      "rank": 1,
      "districtName": "강남구",
      "topProperties": [
        {
          "propertyId": "F009_TARGET_A",
          "propertyName": "F009 테스트아파트 A",
          "address": "서울특별시 강남구 역삼동 100-1",
          "price": 80000,
          "leaseType": "전세",
          "area": 30.0,
          "floor": 10,
          "buildYear": 2020,
          "finalScore": ...,
          "reviewCount": 5,
          "avgRating": 4.0,
          "dataSource": "USER",
          "status": "ACTIVE",
          "ownedByCurrentUser": ...,
          "canEdit": ...,
          "canChangeStatus": ...
        }
      ],
      "averagePriceScore": ...,
      "averageSpaceScore": ...,
      "districtSafetyScore": ...,
      "averageFinalScore": ...,
      "representativeScore": ...
    }
  ]
}
```

핵심 확인 대상:

```text
recommendedDistricts[*].topProperties[*].propertyId == "F009_TARGET_A"
AND
recommendedDistricts[*].topProperties[*].price == 80000
```

주의: `TopCharterPropertyDto`의 `price` 필드는 `PropertyDetail.getDeposit()` 값이 매핑된 것이다.  
Redis Hash 필드명은 `deposit`이지만 응답 DTO 필드명은 `price`이다.

```text
매핑 경로:
  Redis Hash "property:charter:F009_TARGET_A" → deposit = 80000
  → convertHashToPropertyDetail() → PropertyDetail.deposit = 80000
  → convertToTopCharterPropertyDto() → TopCharterPropertyDto.price = 80000
```

#### Reader 측 코드 실행 흐름

다음은 `CharterRecommendationService.getCharterDistrictRecommendations()` 경로에서 강남구 처리 부분의 실행 순서이다.

```text
RecommendationController.getCharterDistrictRecommendations() 진입

1. charterRecommendationService.getCharterDistrictRecommendations(request, userId) 호출

2. strict search 시작 — 서울 25개 자치구별 findValidCharterPropertiesInDistrict() 호출

3. [강남구 처리] findValidCharterPropertiesInDistrict("강남구", request) 진입

4. ZRANGEBYSCORE idx:charterPrice:강남구 40000 60000
   → {F009_TARGET_A(score=50000), F009_DUMMY_B(52000), F009_DUMMY_C(54000)}
   → priceValidIds = {F009_TARGET_A, F009_DUMMY_B, F009_DUMMY_C}

   이 시점 F009_TARGET_A의 가격 ZSet score는 50000(구값)이다.
   Writer는 7-3절 단계 8에서 syncAwait()에 멈춰 있으므로
   ZSet 갱신(단계 10)은 아직 실행되지 않았다.

5. ZRANGEBYSCORE idx:area:강남구:전세 20 40
   → {F009_TARGET_A(30), F009_DUMMY_B(31), F009_DUMMY_C(32)}
   → areaValidIds = {F009_TARGET_A, F009_DUMMY_B, F009_DUMMY_C}

6. priceValidIds.retainAll(areaValidIds)
   → 교집합: {F009_TARGET_A, F009_DUMMY_B, F009_DUMMY_C}
   → F009_TARGET_A가 후보로 확정된다.

7. f009RaceLatch.releaseWriterIfTargetIncluded(priceValidIds)
   → priceValidIds에 F009_TARGET_A가 포함되어 있으므로
     writerReleaseLatch.countDown() 호출
   → Writer 스레드 재개 (7-3절 단계 9부터 실행)

8. findValidCharterPropertiesInDistrict 반환
   → candidateIds = [F009_TARGET_A, F009_DUMMY_B, F009_DUMMY_C]

9. [나머지 자치구 처리 완료]

10. calculateCharterPropertyScores() 진입

11. 전체 후보 매물 ID 취합 → reviewStatisticsRepository.findAllById() 호출
    → F009_TARGET_A, F009_DUMMY_B, F009_DUMMY_C의 리뷰 통계 조회
    (RDB에서 조회 — 13-4절에서 삽입한 데이터)

12. [강남구 점수 계산]
    getMultipleCharterPropertiesFromRedis(candidateIds) 호출
    → Redis Pipeline으로 각 매물 Hash 일괄 조회
    → HGETALL property:charter:F009_TARGET_A
      → deposit = 80000 (신값)
         Writer가 7-3절 단계 7에서 갱신한 값이다.
         이 시점 Writer는 이미 7-3절 단계 10까지 진행했거나 진행 중이다.

13. convertHashToPropertyDetail()
    → PropertyDetail.deposit = 80000

14. getCharterBoundsFromRedis("강남구") → bounds 조회
    getDistrictSafetyScore("강남구") → safety 점수 조회

15. calculatePriceScore(80000, bounds)
    → deposit=80000 기준으로 가격 점수 계산
    calculateSpaceScore(30, bounds)
    → 평수 점수 계산

16. 하이브리드 점수 계산 (가격+평수+안전성 가중치 합 × 0.5 + 리뷰 점수 × 0.5)
    → finalScore 산출
    → 점수 기준 정렬 → 상위 3건 선택

17. convertToTopCharterPropertyDto(propertyDetail, scores, userId)
    → TopCharterPropertyDto.price = propertyDetail.getDeposit() = 80000

18. 응답 조립 → HTTP 200 반환
```

### 7-5. Writer/Reader 타이밍 다이어그램

```text
시간  Writer (PATCH 수정 API)                    Reader (POST 추천 조회 API)
────  ──────────────────────────                 ──────────────────────────────
T0    요청 수신                                   (Constant Timer 대기 중)
T1    findById → entity 조회
T2    verifyActiveForUpdate(ACTIVE) 통과
T3    entity.setDeposit(80000L)
T4    charterRepository.save() [RDB 커밋]
T5    syncRedisAfterUpdate 진입
T6    Hash putAll [deposit=80000]
T7    syncAwait() — 정지 ●                        (Constant Timer 대기 중)
      │                                           │
      │  ← Redis 중간 상태 →                       │
      │  Hash: deposit=80000 (신값)                │
      │  ZSet: score=50000 (구값)                  │
      │                                           │
T8    │                                           요청 수신
T9    │                                           strict search 시작
T10   │                                           [강남구] ZRANGEBYSCORE 가격 40000~60000
      │                                           → F009_TARGET_A 포함 (score=50000)
T11   │                                           [강남구] ZRANGEBYSCORE 평수 20~40
      │                                           → F009_TARGET_A 포함 (score=30)
T12   │                                           retainAll → F009_TARGET_A 후보 확정
T13   │ ← countDown()                             releaseWriterIfTargetIncluded() 호출
      │                                           │
T14   syncAwait() 해제 ○                          findValidCharterPropertiesInDistrict 반환
T15   ZSet add [score=80000]                      │
T16   bounds 갱신                                  나머지 자치구 처리
T17   응답 반환 (HTTP 200)                          calculateCharterPropertyScores 진입
T18                                               HGETALL property:charter:F009_TARGET_A
                                                  → deposit=80000 (신값)
T19                                               점수 계산 + 정렬
T20                                               TopCharterPropertyDto.price = 80000
T21                                               응답 반환 (HTTP 200)
```

핵심 구간은 T7~T13이다.

```text
T7에서 Writer가 정지한 순간부터 T13에서 Reader가 latch를 해제하는 순간까지,
Redis는 다음 중간 상태를 유지한다:
  Hash deposit = 80000 (신값)
  ZSet score = 50000 (구값)

Reader는 T10에서 ZSet score=50000을 기준으로 F009_TARGET_A를 후보로 선정한다.
이후 T18에서 Hash deposit=80000을 읽는다.

따라서 조회 조건 budgetMax=60000을 초과하는 매물(deposit=80000)이
추천 응답의 topProperties[*].price에 포함된다.
```

---

## 8. 실패 재현 판정 기준

수정 API 응답과 조회 API 응답 자체를 단순 비교하는 것이 핵심이 아니다.

핵심은 조회 API 응답 내부에 다음 모순이 존재하는지 확인하는 것이다.

```text
조회 요청 조건:
budgetMax = 60000

조회 응답:
targetPropertyId = F009_TARGET_A 포함
AND targetPropertyId.deposit = 80000
```

이 상태가 확인되면 다음 결론을 낼 수 있다.

```text
Reader가 ZSet 기반 후보 선정은 수정 전 값으로 수행했고,
Hash 기반 상세 조회는 수정 후 값으로 수행했다.

따라서 Redis Hash/ZSet 순차 쓰기 중간 상태가 Reader에게 관찰되었다.
```

이것이 F009 UPDATE 경합의 재현 성공 기준이다.

---

## 9. MULTI/EXEC 적용 후 동일 시나리오의 기대 결과

MULTI/EXEC 적용 후에는 같은 JMeter 시나리오에서 금지 상태가 나오면 안 된다.

허용 상태는 다음 두 가지뿐이다.

### 9-1. Reader가 수정 전 전체 상태를 본 경우

```text
추천 응답에 F009_TARGET_A 포함 가능
deposit = 50000
```

이 경우는 정상이다.  
ZSet과 Hash가 모두 수정 전 상태이므로 일관성이 있다.

### 9-2. Reader가 수정 후 전체 상태를 본 경우

```text
추천 응답에 F009_TARGET_A 미포함
```

이 경우도 정상이다.  
가격 ZSet score가 80000으로 갱신되었기 때문에 budgetMax=60000 조건에서 탈락한다.

### 9-3. 금지 상태

```text
추천 응답에 F009_TARGET_A 포함
AND deposit = 80000
AND 요청 budgetMax = 60000
```

이 상태가 MULTI/EXEC 적용 후에도 발생하면 F009 문제는 해결되지 않은 것이다.

---

## 10. 문서화 시 사용할 결론 문장

테스트 결과 보고서에는 다음 취지로 정리한다.

```text
본 테스트는 Postman/JMeter 요청 타이밍에 의존하지 않고,
Writer의 Redis Hash 갱신 직후와 ZSet 갱신 직전 사이를 CountDownLatch로 고정하였다.

이후 추천 조회 API가 기존 서비스 흐름 그대로 가격 ZSet과 평수 ZSet의 교집합 후보를 확정하게 한 뒤,
Writer를 재개시켜 ZSet을 갱신하도록 구성하였다.

그 결과 추천 조회 API는 구 ZSet 기준으로 후보를 선정하고,
이후 신 Hash 값을 읽어 budgetMax=60000 조건에서 deposit=80000 매물을 응답에 포함하였다.

이는 Redis Hash와 ZSet을 개별 커맨드로 순차 갱신하는 현재 구조에서
Reader가 Writer의 중간 상태를 관찰할 수 있음을 보여준다.
```

---

## 11. 주의 사항

1. 테스트 전용 latch 코드는 운영 profile에서 절대 활성화하지 않는다.
2. 테스트 대상 propertyId를 명시적으로 제한한다.
3. timeout 없이 `await()`를 사용하지 않는다.
4. 테스트 반복 전 latch를 반드시 reset한다.
5. 추천 서비스의 fallback이 발생하지 않도록 대상 지역구에 조건을 만족하는 매물을 최소 3개 이상 준비한다.
6. 실패 재현 기준은 수정 API 응답과 조회 API 응답의 단순 비교가 아니라, 조회 API 응답 내부의 조건-데이터 불일치다.
7. MULTI/EXEC 적용 후에는 동일 시나리오에서 금지 상태가 사라지는지 확인한다.

---

# 12. 배치 스케줄러 비활성화

본 테스트는 배치 시스템을 사용하지 않는다.  
배치가 실행되면 Redis 직접 주입 데이터가 초기화되어 F009 중간 상태 재현 전제가 깨진다.

비활성화 방법은 배치 관련 파일 2개에서 `@Scheduled` 어노테이션을 주석 처리하는 것이다.

#### 대상 1: BatchScheduler.java

```text
파일: com.wherehouse.recommand.batch.BatchScheduler.BatchScheduler
```

```java
// 원본
@Scheduled(cron = “0 0 4 * * ?”)
public void executeBatchProcess() {

// F009 테스트 시 주석 처리
// @Scheduled(cron = “0 0 4 * * ?”)
public void executeBatchProcess() {
```

#### 대상 2: RdbSyncListener.java

```text
파일: com.wherehouse.recommand.batch.BatchScheduler.RdbSyncListener
```

```java
// 원본
@Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 1000)
// @EventListener
public void handleDataCollectionCompletedEvent() {

// F009 테스트 시 주석 처리
// @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 1000)
// @EventListener
public void handleDataCollectionCompletedEvent() {
```

#### 기동 후 확인

```text
1. executeBatchProcess() 호출 로그가 발생하지 않는다.
2. handleDataCollectionCompletedEvent() 호출 로그가 발생하지 않는다.
3. Redis 직접 주입 데이터가 애플리케이션 기동 후에도 유지된다.
```

---

# 13. RDBMS 테스트 데이터 직접 주입 SQL

본 테스트는 배치 시스템을 사용하지 않으므로 RDBMS에 전세 사용자 매물 3건을 직접 삽입한다.

## 13-1. 테스트 식별자

```text
대상 지역구: 강남구
시군구 코드: 11680
임대 유형: 전세
테스트 대상 매물: F009_TARGET_A
더미 매물: F009_DUMMY_B, F009_DUMMY_C
```

조회 조건은 다음이다.

```text
budgetMin = 40000
budgetMax = 60000
areaMin = 20
areaMax = 40
```

수정 요청은 다음이다.

```text
F009_TARGET_A deposit: 50000 → 80000
```

## 13-2. 기존 테스트 데이터 정리

Oracle 기준 SQL이다.

```sql
DELETE FROM REVIEW_STATISTICS
WHERE PROPERTY_ID IN ('F009_TARGET_A', 'F009_DUMMY_B', 'F009_DUMMY_C');

DELETE FROM PROPERTIES_CHARTER
WHERE PROPERTY_ID IN ('F009_TARGET_A', 'F009_DUMMY_B', 'F009_DUMMY_C');

COMMIT;
```

## 13-3. PROPERTIES_CHARTER 테스트 데이터 삽입

아래 INSERT는 사용자 매물 고도화 설계 명세서의 `PROPERTIES_CHARTER` 확장 컬럼과 추천 시스템 설계 명세서의 전세 매물 필드를 기준으로 작성한다.

```sql
INSERT INTO PROPERTIES_CHARTER (
    PROPERTY_ID,
    APT_NM,
    EXCLU_USE_AR,
    FLOOR,
    BUILD_YEAR,
    DEAL_DATE,
    DEPOSIT,
    LEASE_TYPE,
    UMD_NM,
    JIBUN,
    SGG_CD,
    ADDRESS,
    AREA_IN_PYEONG,
    DISTRICT_NAME,
    DATA_SOURCE,
    STATUS,
    REGISTERED_USER_ID,
    REGISTERED_AT,
    MODIFIED_AT,
    USER_PROPOSED_DEPOSIT
) VALUES (
    'F009_TARGET_A',
    'F009 테스트아파트 A',
    99.17,
    10,
    2020,
    DATE '2026-05-06',
    50000,
    '전세',
    '역삼동',
    '100-1',
    '11680',
    '강남구 역삼동 100-1',
    30,
    '강남구',
    'USER',
    'ACTIVE',
    'f009-user',
    SYSTIMESTAMP,
    NULL,
    NULL
);

INSERT INTO PROPERTIES_CHARTER (
    PROPERTY_ID,
    APT_NM,
    EXCLU_USE_AR,
    FLOOR,
    BUILD_YEAR,
    DEAL_DATE,
    DEPOSIT,
    LEASE_TYPE,
    UMD_NM,
    JIBUN,
    SGG_CD,
    ADDRESS,
    AREA_IN_PYEONG,
    DISTRICT_NAME,
    DATA_SOURCE,
    STATUS,
    REGISTERED_USER_ID,
    REGISTERED_AT,
    MODIFIED_AT,
    USER_PROPOSED_DEPOSIT
) VALUES (
    'F009_DUMMY_B',
    'F009 테스트아파트 B',
    102.48,
    11,
    2020,
    DATE '2026-05-06',
    52000,
    '전세',
    '역삼동',
    '100-2',
    '11680',
    '강남구 역삼동 100-2',
    31,
    '강남구',
    'USER',
    'ACTIVE',
    'f009-user',
    SYSTIMESTAMP,
    NULL,
    NULL
);

INSERT INTO PROPERTIES_CHARTER (
    PROPERTY_ID,
    APT_NM,
    EXCLU_USE_AR,
    FLOOR,
    BUILD_YEAR,
    DEAL_DATE,
    DEPOSIT,
    LEASE_TYPE,
    UMD_NM,
    JIBUN,
    SGG_CD,
    ADDRESS,
    AREA_IN_PYEONG,
    DISTRICT_NAME,
    DATA_SOURCE,
    STATUS,
    REGISTERED_USER_ID,
    REGISTERED_AT,
    MODIFIED_AT,
    USER_PROPOSED_DEPOSIT
) VALUES (
    'F009_DUMMY_C',
    'F009 테스트아파트 C',
    105.79,
    12,
    2020,
    DATE '2026-05-06',
    54000,
    '전세',
    '역삼동',
    '100-3',
    '11680',
    '강남구 역삼동 100-3',
    32,
    '강남구',
    'USER',
    'ACTIVE',
    'f009-user',
    SYSTIMESTAMP,
    NULL,
    NULL
);

COMMIT;
```

## 13-4. REVIEW_STATISTICS 테스트 데이터 삽입

현재 `CharterRecommendationService`는 후보 매물 ID를 기준으로 `reviewStatisticsRepository.findAllById()`를 호출한다.  
이후 `reviewCount`, `avgRating`, `positiveKeywordCount`, `negativeKeywordCount`를 사용하여 하이브리드 점수를 계산한다.

따라서 테스트 매물이 점수 계산 루프에서 제외되지 않도록 REVIEW_STATISTICS 데이터를 함께 삽입한다.

```sql
INSERT INTO REVIEW_STATISTICS (
    PROPERTY_ID,
    REVIEW_COUNT,
    AVG_RATING,
    POSITIVE_KEYWORD_COUNT,
    NEGATIVE_KEYWORD_COUNT
) VALUES (
    'F009_TARGET_A',
    5,
    4.0,
    3,
    1
);

INSERT INTO REVIEW_STATISTICS (
    PROPERTY_ID,
    REVIEW_COUNT,
    AVG_RATING,
    POSITIVE_KEYWORD_COUNT,
    NEGATIVE_KEYWORD_COUNT
) VALUES (
    'F009_DUMMY_B',
    5,
    4.0,
    3,
    1
);

INSERT INTO REVIEW_STATISTICS (
    PROPERTY_ID,
    REVIEW_COUNT,
    AVG_RATING,
    POSITIVE_KEYWORD_COUNT,
    NEGATIVE_KEYWORD_COUNT
) VALUES (
    'F009_DUMMY_C',
    5,
    4.0,
    3,
    1
);

COMMIT;
```

## 13-5. RDBMS 주입 확인 쿼리

```sql
SELECT
    PROPERTY_ID,
    APT_NM,
    DEPOSIT,
    AREA_IN_PYEONG,
    DISTRICT_NAME,
    DATA_SOURCE,
    STATUS,
    REGISTERED_USER_ID,
    REGISTERED_AT,
    MODIFIED_AT,
    USER_PROPOSED_DEPOSIT
FROM PROPERTIES_CHARTER
WHERE PROPERTY_ID IN ('F009_TARGET_A', 'F009_DUMMY_B', 'F009_DUMMY_C')
ORDER BY PROPERTY_ID;

SELECT
    PROPERTY_ID,
    REVIEW_COUNT,
    AVG_RATING,
    POSITIVE_KEYWORD_COUNT,
    NEGATIVE_KEYWORD_COUNT
FROM REVIEW_STATISTICS
WHERE PROPERTY_ID IN ('F009_TARGET_A', 'F009_DUMMY_B', 'F009_DUMMY_C')
ORDER BY PROPERTY_ID;
```

수정 API 실행 후에는 다음을 확인한다.

```sql
SELECT
    PROPERTY_ID,
    DEPOSIT,
    STATUS,
    MODIFIED_AT
FROM PROPERTIES_CHARTER
WHERE PROPERTY_ID = 'F009_TARGET_A';
```

수정 API가 정상 완료된 뒤에는 `F009_TARGET_A`의 `DEPOSIT`이 `80000`이어야 한다.

---

# 14. Redis 테스트 데이터 직접 주입 명령어

본 테스트는 배치 시스템을 사용하지 않으므로 Redis에 테스트 데이터를 직접 주입한다.

Redis 키 구조는 설계 명세서의 전세 저장 구조를 따른다.

```text
매물 Hash:
property:charter:{id}

전세금 인덱스:
idx:charterPrice:{district}

전세 평수 인덱스:
idx:area:{district}:전세

전세 정규화 범위:
bounds:{district}:전세

안전성 점수:
safety:{district}
```

## 14-1. Redis 접속

```bash
redis-cli
```

비밀번호가 있는 경우:

```bash
redis-cli -a <password>
```

호스트/포트 지정이 필요한 경우:

```bash
redis-cli -h 127.0.0.1 -p 6379
```

## 14-2. 기존 테스트 Redis 데이터 정리

```bash
redis-cli DEL property:charter:F009_TARGET_A property:charter:F009_DUMMY_B property:charter:F009_DUMMY_C

redis-cli ZREM idx:charterPrice:강남구 F009_TARGET_A F009_DUMMY_B F009_DUMMY_C

redis-cli ZREM idx:area:강남구:전세 F009_TARGET_A F009_DUMMY_B F009_DUMMY_C
```

bounds와 safety를 테스트 전용 값으로 완전히 재설정하려면 다음도 수행한다.

```bash
redis-cli DEL bounds:강남구:전세 safety:강남구
```

주의:

```text
bounds:강남구:전세와 safety:강남구를 삭제하면 로컬의 기존 추천 테스트 데이터에 영향을 줄 수 있다.
F009 전용 로컬 Redis DB를 사용하거나 테스트 직후 원상 복구한다.
```

## 14-3. Bounds와 Safety 주입

설계 명세서 기준 전세 bounds 필드는 `minPrice`, `maxPrice`, `minArea`, `maxArea`이다.  
기존 추천 시스템 명세서에는 `propertyCount`, `lastUpdated`도 전세 bounds 필드로 정의되어 있으므로 함께 넣는다.

```bash
redis-cli HSET bounds:강남구:전세 \
  minPrice 40000 \
  maxPrice 80000 \
  minArea 20 \
  maxArea 40 \
  propertyCount 3 \
  lastUpdated "2026-05-06T00:00:00"
```

설계 명세서 기준 safety 필드는 `districtName`, `safetyScore`, `lastUpdated`, `version`이다.

```bash
redis-cli HSET safety:강남구 \
  districtName "강남구" \
  safetyScore 70 \
  lastUpdated "2026-05-06T00:00:00" \
  version "1.0"
```

## 14-4. 대상 매물 A Hash 주입

Redis Hash 필드는 기존 추천 시스템 명세서의 전세 매물 Hash 필드와 사용자 매물 고도화 설계서의 신규 필드를 함께 반영한다.

```bash
redis-cli HSET property:charter:F009_TARGET_A \
  propertyId F009_TARGET_A \
  aptNm "F009 테스트아파트 A" \
  excluUseAr 99.17 \
  floor 10 \
  buildYear 2020 \
  dealDate "2026-05-06" \
  deposit 50000 \
  monthlyRent 0 \
  leaseType "전세" \
  umdNm "역삼동" \
  jibun "100-1" \
  sggCd 11680 \
  address "강남구 역삼동 100-1" \
  areaInPyeong 30 \
  rgstDate "2026-05-06" \
  districtName "강남구" \
  dataSource USER \
  status ACTIVE \
  registeredUserId f009-user \
  registeredAt "2026-05-06T00:00:00" \
  modifiedAt ""
```

## 14-5. 더미 매물 B Hash 주입

```bash
redis-cli HSET property:charter:F009_DUMMY_B \
  propertyId F009_DUMMY_B \
  aptNm "F009 테스트아파트 B" \
  excluUseAr 102.48 \
  floor 11 \
  buildYear 2020 \
  dealDate "2026-05-06" \
  deposit 52000 \
  monthlyRent 0 \
  leaseType "전세" \
  umdNm "역삼동" \
  jibun "100-2" \
  sggCd 11680 \
  address "강남구 역삼동 100-2" \
  areaInPyeong 31 \
  rgstDate "2026-05-06" \
  districtName "강남구" \
  dataSource USER \
  status ACTIVE \
  registeredUserId f009-user \
  registeredAt "2026-05-06T00:00:00" \
  modifiedAt ""
```

## 14-6. 더미 매물 C Hash 주입

```bash
redis-cli HSET property:charter:F009_DUMMY_C \
  propertyId F009_DUMMY_C \
  aptNm "F009 테스트아파트 C" \
  excluUseAr 105.79 \
  floor 12 \
  buildYear 2020 \
  dealDate "2026-05-06" \
  deposit 54000 \
  monthlyRent 0 \
  leaseType "전세" \
  umdNm "역삼동" \
  jibun "100-3" \
  sggCd 11680 \
  address "강남구 역삼동 100-3" \
  areaInPyeong 32 \
  rgstDate "2026-05-06" \
  districtName "강남구" \
  dataSource USER \
  status ACTIVE \
  registeredUserId f009-user \
  registeredAt "2026-05-06T00:00:00" \
  modifiedAt ""
```

## 14-7. 가격 ZSet 주입

전세금 기준 인덱스는 `idx:charterPrice:{지역구명}`이고, score는 전세금, member는 매물 ID이다.

```bash
redis-cli ZADD idx:charterPrice:강남구 \
  50000 F009_TARGET_A \
  52000 F009_DUMMY_B \
  54000 F009_DUMMY_C
```

## 14-8. 평수 ZSet 주입

전세 평수 기준 인덱스는 `idx:area:{지역구명}:전세`이고, score는 평수, member는 매물 ID이다.

```bash
redis-cli ZADD idx:area:강남구:전세 \
  30 F009_TARGET_A \
  31 F009_DUMMY_B \
  32 F009_DUMMY_C
```

## 14-9. Redis 주입 후 확인

```bash
redis-cli HGETALL property:charter:F009_TARGET_A
redis-cli HGETALL property:charter:F009_DUMMY_B
redis-cli HGETALL property:charter:F009_DUMMY_C

redis-cli ZRANGEBYSCORE idx:charterPrice:강남구 40000 60000 WITHSCORES
redis-cli ZRANGEBYSCORE idx:area:강남구:전세 20 40 WITHSCORES

redis-cli HGETALL bounds:강남구:전세
redis-cli HGETALL safety:강남구
```

기대 결과:

```text
idx:charterPrice:강남구 40000~60000
- F009_TARGET_A 50000
- F009_DUMMY_B 52000
- F009_DUMMY_C 54000

idx:area:강남구:전세 20~40
- F009_TARGET_A 30
- F009_DUMMY_B 31
- F009_DUMMY_C 32
```

---

# 15. 로컬 CLI 수동 관찰 명령어

F009 재현 중에는 Redis 상태를 로컬 CLI에서 직접 확인한다.

## 15-1. 수정 API 호출 전 상태 확인

```bash
redis-cli HGET property:charter:F009_TARGET_A deposit
redis-cli ZSCORE idx:charterPrice:강남구 F009_TARGET_A
```

기대 결과:

```text
HGET deposit = 50000
ZSCORE = 50000
```

## 15-2. Writer가 syncAwait에서 멈춘 상태 확인

매물 수정 API를 호출한 뒤, Writer가 `syncAwait()`에서 멈추면 다음 상태가 되어야 한다.

```bash
redis-cli HGET property:charter:F009_TARGET_A deposit
redis-cli ZSCORE idx:charterPrice:강남구 F009_TARGET_A
```

기대 결과:

```text
HGET deposit = 80000
ZSCORE = 50000
```

이 상태가 F009 UPDATE 경합의 핵심 중간 상태다.

## 15-3. 조회 API가 targetPropertyId를 후보로 잡을 수 있는지 확인

```bash
redis-cli ZRANGEBYSCORE idx:charterPrice:강남구 40000 60000 WITHSCORES

redis-cli ZRANGEBYSCORE idx:area:강남구:전세 20 40 WITHSCORES
```

기대 결과:

```text
가격 ZSet 조회 결과에 F009_TARGET_A 포함
평수 ZSet 조회 결과에 F009_TARGET_A 포함
```

즉, Reader의 `findValidCharterPropertiesInDistrict()`는 `F009_TARGET_A`를 후보로 확정할 수 있다.

## 15-4. Writer가 해제된 뒤 상태 확인

추천 조회 API가 `findValidCharterPropertiesInDistrict()`에서 `releaseWriterIfTargetIncluded()`를 호출하면 Writer가 해제되고 ZSet이 갱신된다.

```bash
redis-cli HGET property:charter:F009_TARGET_A deposit
redis-cli ZSCORE idx:charterPrice:강남구 F009_TARGET_A
```

기대 결과:

```text
HGET deposit = 80000
ZSCORE = 80000
```

이 시점 이후 신규 조회에서는 `budgetMax=60000` 조건에 따라 `F009_TARGET_A`가 가격 ZSet 필터에서 탈락해야 한다.

## 15-5. 최종 정상 상태 확인

```bash
redis-cli ZRANGEBYSCORE idx:charterPrice:강남구 40000 60000 WITHSCORES
```

기대 결과:

```text
F009_TARGET_A는 더 이상 포함되지 않음
F009_DUMMY_B, F009_DUMMY_C는 포함 가능
```

---

# 16. JMeter 요청 본문 및 Assertion 기준

## 16-1. 추천 조회 API 요청

전세 추천 API는 다음 엔드포인트를 사용한다.

```text
POST /api/recommendations/charter-districts
```

요청 본문:

```json
{
  "budgetMin": 40000,
  "budgetMax": 60000,
  "areaMin": 20.0,
  "areaMax": 40.0,
  "priority1": "PRICE",
  "priority2": "SAFETY",
  "priority3": "SPACE",
  "budgetFlexibility": 0,
  "minSafetyScore": 0,
  "absoluteMinArea": 0.0
}
```

## 16-2. 조회 API 응답 내 금지 상태

전세 추천 응답의 매물 전세금 필드는 `topProperties[*].price`이다.

따라서 JMeter Assertion은 다음 금지 상태를 확인한다.

```text
recommendedDistricts[*].topProperties[*].propertyId == "F009_TARGET_A"
AND
recommendedDistricts[*].topProperties[*].price == 80000
AND
요청 budgetMax == 60000
```

이 조건이 참이면 다음을 의미한다.

```text
Reader는 가격 ZSet score=50000 기준으로 F009_TARGET_A를 후보에 포함했다.
그러나 이후 Hash 상세 조회에서는 deposit=80000을 읽었다.
즉, 조회 조건과 응답 매물 가격이 모순된다.
```

---

# 17. 전체 테스트 실행 순서 보강본

최종 테스트 순서는 다음이다.

```text
1. 배치 스케줄러를 비활성화한다. (12절 참조)
   BatchScheduler.java의 @Scheduled(cron = "0 0 4 * * ?") 주석 처리
   RdbSyncListener.java의 @Scheduled(fixedDelay = Long.MAX_VALUE, initialDelay = 1000) 주석 처리

2. 애플리케이션을 f009-test profile로 실행한다.

3. RDBMS에서 F009 테스트 매물 3건을 PROPERTIES_CHARTER에 직접 삽입한다.

4. RDBMS에서 F009 테스트 매물 3건의 REVIEW_STATISTICS 데이터를 직접 삽입한다.

5. Redis에서 F009 테스트 Hash, 가격 ZSet, 평수 ZSet, bounds, safety 데이터를 직접 주입한다.

6. Redis CLI로 초기 상태를 확인한다.
   F009_TARGET_A Hash deposit = 50000
   F009_TARGET_A price ZSet score = 50000

7. f009-test 프로파일 기동 시 F009RaceLatch가 @PostConstruct로 자동 활성화된 상태를 확인한다.

8. JMeter에서 매물 수정 API를 먼저 호출한다.
   deposit 50000 → 80000

9. Writer는 syncRedisAfterUpdate()에서 Hash 갱신 후 syncAwait()에 의해 정지한다.

10. Redis CLI로 중간 상태를 확인한다.
    F009_TARGET_A Hash deposit = 80000
    F009_TARGET_A price ZSet score = 50000

11. JMeter에서 추천 조회 API를 호출한다.
    POST /api/recommendations/charter-districts
    budgetMin=40000
    budgetMax=60000
    areaMin=20
    areaMax=40

12. Reader는 findValidCharterPropertiesInDistrict()에서 구 ZSet 기준으로 F009_TARGET_A를 후보로 확정한다.

13. Reader는 releaseWriterIfTargetIncluded()를 호출하여 Writer를 해제한다.

14. Writer는 가격 ZSet score를 80000으로 갱신하고 수정 API 응답을 완료한다.

15. Reader는 이미 확보한 후보 ID 목록으로 Hash 상세를 조회한다.
    이때 F009_TARGET_A의 deposit=80000을 읽고, 응답 DTO의 topProperties[*].price에 80000이 매핑된다.

16. JMeter 조회 API 응답에서 다음 금지 상태가 발생했는지 확인한다.
    recommendedDistricts[*].topProperties[*].propertyId == F009_TARGET_A
    AND recommendedDistricts[*].topProperties[*].price == 80000
    AND 요청 budgetMax == 60000

17. 금지 상태가 확인되면 F009 Write-Read 경합 재현 성공으로 판정한다.

18. MULTI/EXEC 적용 후 동일 시나리오를 다시 수행한다.

19. MULTI/EXEC 적용 후에는 다음 금지 상태가 사라져야 한다.
    recommendedDistricts[*].topProperties[*].propertyId == F009_TARGET_A
    AND recommendedDistricts[*].topProperties[*].price == 80000
    AND 요청 budgetMax == 60000
```
