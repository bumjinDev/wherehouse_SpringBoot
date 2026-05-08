# F009 Write-Read 경합 재현 테스트 결과 보고서

| 항목 | 내용 |
|------|------|
| **문서 ID** | F009-TEST-REPORT-001 |
| **테스트 일시** | 2026-05-07 17:39 (KST) |
| **테스트 수행자** | bumjinDev |
| **테스트 도구** | Apache JMeter 5.6.3 |
| **테스트 대상** | Wherehouse 전세 매물 수정 API + 전세 추천 조회 API |
| **판정 결과** | **경합 재현 성공 (금지 상태 확인)** |

---

## 1. 테스트 목적

Redis Hash와 ZSet 인덱스를 개별 커맨드로 순차 갱신하는 현재 구조에서, 매물 수정(Writer)과 추천 조회(Reader)가 동시 실행될 때 Reader가 Writer의 **중간 상태**(Hash=신값, ZSet=구값)를 관찰하여 조회 조건과 모순되는 매물을 응답에 포함하는지 검증한다.

---

## 2. 문제 정의

### 2.1 경합 시나리오

```
전제:
  매물 A의 기존 deposit = 50,000 (만원)
  매물 A의 가격 ZSet score = 50,000
  사용자 조회 조건: budgetMax = 60,000

수정 요청:
  매물 A의 deposit을 80,000으로 변경

위험 시퀀스:
  1. Writer가 Redis Hash를 먼저 갱신한다.    → deposit = 80,000
  2. Writer가 아직 ZSet을 갱신하지 못한다.    → score = 50,000 (구값)
  3. Reader가 ZSet으로 후보를 선정한다.        → A는 score=50,000이므로 후보에 포함
  4. Reader가 Hash에서 상세를 조회한다.        → deposit = 80,000 (신값)

결과:
  budgetMax=60,000인데 deposit=80,000 매물이 추천 응답에 포함
```

### 2.2 판정 기준 (Pass/Fail)

| 조건 | 판정 |
|------|------|
| Reader 응답에 TARGET_A 포함 AND price=80,000 | **경합 재현 성공 (금지 상태)** |
| Reader 응답에 TARGET_A 포함 AND price=50,000 | 정상 (수정 전 일관된 상태) |
| Reader 응답에 TARGET_A 미포함 | 정상 (수정 후 일관된 상태) |

---

## 3. 테스트 환경

### 3.1 인프라 환경

| 구성 요소 | 버전/사양 |
|-----------|-----------|
| OS | Windows 11 Pro |
| JDK | 17+ |
| Spring Boot | 3.x |
| Oracle DB | RDBMS (PROPERTIES_CHARTER, REVIEW_STATISTICS) |
| Redis | 127.0.0.1:6379 (Standalone) |
| JMeter | 5.6.3 |

### 3.2 애플리케이션 설정

| 설정 항목 | 값 | 비고 |
|-----------|-----|------|
| 서버 포트 | 8185 | |
| 컨텍스트 패스 | /wherehouse | |
| F009RaceLatch | 활성 | `@Profile` 주석 처리로 전체 프로파일 활성 |
| `@Transactional` (updateProperty) | 비활성 | 테스트 목적 주석 처리 |
| RdbSyncListener `@Scheduled` | 활성 | 부팅 시 RDB→Redis 자동 동기화 |
| BatchScheduler `@Scheduled` | 비활성 | 배치 간섭 방지 |

### 3.3 테스트 동기화 메커니즘

본 테스트는 `Thread.sleep()`이나 JMeter 타이밍에 의존하지 않는다.
애플리케이션 코드에 삽입된 `F009RaceLatch`(`CountDownLatch` 기반)가 Writer와 Reader 간 실행 순서를 구조적으로 보장한다.

```
F009RaceLatch 동작 원리:

  Writer 측 (CharterPropertyWriteService.syncRedisAfterUpdate):
    Hash putAll 완료 직후 → syncAwait() 호출 → CountDownLatch.await(30초)로 블로킹

  Reader 측 (CharterRecommendationService.findValidCharterPropertiesInDistrict):
    ZSet 교집합 후보 확정 직후 → releaseWriterIfTargetIncluded() 호출 → countDown()

  결과:
    Reader가 반드시 "Hash=신값, ZSet=구값" 상태에서 후보를 선정하게 된다.
```

---

## 4. 테스트 데이터

### 4.1 RDBMS 데이터 (PROPERTIES_CHARTER 3건)

| Property ID | 역할 | Deposit | Area(평) | District | Status |
|-------------|------|---------|----------|----------|--------|
| `F009_TARGET_A0000000000000000000` | 수정 대상 | 50,000 → 80,000 | 30 | 강남구 | ACTIVE |
| `F009_DUMMY_B00000000000000000000` | strict search 충족용 | 52,000 | 31 | 강남구 | ACTIVE |
| `F009_DUMMY_C00000000000000000000` | strict search 충족용 | 54,000 | 32 | 강남구 | ACTIVE |

### 4.2 RDBMS 데이터 (REVIEW_STATISTICS 3건)

| Property ID | Review Count | Avg Rating | Positive | Negative |
|-------------|-------------|------------|----------|----------|
| `F009_TARGET_A0000000000000000000` | 5 | 4.0 | 3 | 1 |
| `F009_DUMMY_B00000000000000000000` | 5 | 4.0 | 3 | 1 |
| `F009_DUMMY_C00000000000000000000` | 5 | 4.0 | 3 | 1 |

### 4.3 Redis 초기 데이터

**매물 Hash** (`property:charter:{id}`)

3건 모두 적재. 주요 필드: `propertyId`, `deposit`, `areaInPyeong`, `districtName`, `leaseType`, `dataSource`, `status`.

**가격 ZSet** (`idx:charterPrice:강남구`)

| Member | Score |
|--------|-------|
| `F009_TARGET_A0000000000000000000` | 50,000 |
| `F009_DUMMY_B00000000000000000000` | 52,000 |
| `F009_DUMMY_C00000000000000000000` | 54,000 |

**평수 ZSet** (`idx:area:강남구:전세`)

| Member | Score |
|--------|-------|
| `F009_TARGET_A0000000000000000000` | 30 |
| `F009_DUMMY_B00000000000000000000` | 31 |
| `F009_DUMMY_C00000000000000000000` | 32 |

**Bounds** (`bounds:강남구:전세`): minPrice=40,000, maxPrice=80,000, minArea=20, maxArea=40

**Safety** (`safety:강남구`): safetyScore=70

### 4.4 Redis 데이터 주입 방식

`RdbSyncListener`의 `@Scheduled(fixedDelay=Long.MAX_VALUE, initialDelay=1000)`을 활성화하여 애플리케이션 부팅 시 RDB→Redis 자동 동기화를 수행하였다. 수동 Redis CLI 주입은 사용하지 않았다.

---

## 5. JMeter 테스트 구성

### 5.1 테스트 플랜 구조

```
F009 Write-Read Race Reproduction Test
├── Thread Group 1 — Writer (매물 수정)
│   ├── HTTP Header Manager
│   ├── PATCH 매물 수정 — deposit 50000→80000
│   └── View Results Tree — Writer
│
└── Thread Group 2 — Reader (추천 조회)
    ├── HTTP Header Manager
    ├── Constant Timer — 2000ms (Writer latch 도달 대기)
    ├── POST 추천 조회 — 전세 지역구
    ├── Assertion — F009_TARGET_A 포함 확인
    ├── Assertion — 금지 상태 (price=80000)
    └── View Results Tree — Reader
```

### 5.2 Thread Group 설정

| 항목 | Writer | Reader |
|------|--------|--------|
| Thread 수 | 1 | 1 |
| Ramp-Up | 0초 | 0초 |
| Loop Count | 1 | 1 |
| Startup Delay | 0초 | 0초 |
| Connect Timeout | 5,000ms | 5,000ms |
| Response Timeout | 60,000ms | 60,000ms |

### 5.3 Writer 요청 (PATCH)

```
Method : PATCH
URL    : http://localhost:8185/wherehouse/api/v1/properties/charter/F009_TARGET_A0000000000000000000
Headers: Content-Type: application/json
         Cookie: Authorization={JWT_TOKEN}

Body:
{
  "deposit": 80000
}
```

Response Timeout을 60초로 설정한 이유: `syncAwait()`의 latch 대기 시간 30초 + 후속 처리 마진.

### 5.4 Reader 요청 (POST)

```
Method : POST
URL    : http://localhost:8185/wherehouse/api/recommendations/charter-districts
Headers: Content-Type: application/json
         Cookie: Authorization={JWT_TOKEN}

Body:
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

### 5.5 Reader Constant Timer

Writer가 `syncAwait()`에 도달하는 데 약 100ms 소요된다. Reader에 2,000ms Constant Timer를 설정하여 Writer가 Hash 갱신 후 latch에서 블로킹 상태에 진입한 이후에 Reader 요청이 시작되도록 마진을 확보하였다.

---

## 6. 테스트 실행 결과

### 6.1 Redis 상태 — 테스트 전 (초기 상태)

```
> HGET property:charter:F009_TARGET_A0000000000000000000 deposit
"\"50000\""

> ZSCORE idx:charterPrice:강남구 "\"F009_TARGET_A0000000000000000000\""
"50000"
```

**판정: Hash deposit=50,000, ZSet score=50,000 — 정합 상태 확인.**

### 6.2 Writer 응답

| 항목 | 값 |
|------|-----|
| HTTP Status | 200 OK |
| Content-Type | application/json |
| Date | Thu, 07 May 2026 08:39:29 GMT |

```json
{
  "property_id": "F009_TARGET_A0000000000000000000",
  "modified_at": "2026-05-07T17:39:27",
  "changed_fields": ["deposit"]
}
```

**판정: deposit 필드 수정 정상 완료.**

### 6.3 Reader 응답

| 항목 | 값 |
|------|-----|
| HTTP Status | 200 OK |
| Content-Type | application/json |
| Date | Thu, 07 May 2026 08:39:29 GMT |

```json
{
  "searchStatus": "SUCCESS_NORMAL",
  "message": "조건에 맞는 전세 매물을 성공적으로 찾았습니다.",
  "recommendedDistricts": [
    {
      "rank": 1,
      "district_name": "강남구",
      "summary": "가격 1순위 조건에 가장 부합하며, 조건 내 추천 매물이 3건 있습니다.",
      "top_properties": [
        {
          "property_id": "F009_DUMMY_C00000000000000000000",
          "property_name": "F009 테스트아파트 C",
          "address": "서울특별시 강남구 역삼동 100-3",
          "price": 54000,
          "lease_type": "전세",
          "area": 32.0,
          "floor": 12,
          "build_year": 2020,
          "final_score": 72.8146,
          "review_count": 5,
          "avg_rating": 4.0,
          "owned_by_current_user": false,
          "can_edit": true,
          "can_change_status": false
        },
        {
          "property_id": "F009_DUMMY_B00000000000000000000",
          "property_name": "F009 테스트아파트 B",
          "address": "서울특별시 강남구 역삼동 100-2",
          "price": 52000,
          "lease_type": "전세",
          "area": 31.0,
          "floor": 11,
          "build_year": 2020,
          "final_score": 72.3146,
          "review_count": 5,
          "avg_rating": 4.0,
          "owned_by_current_user": false,
          "can_edit": true,
          "can_change_status": false
        },
        {
          "property_id": "F009_TARGET_A0000000000000000000",
          "property_name": "F009 테스트아파트 A",
          "address": "서울특별시 강남구 역삼동 100-1",
          "price": 80000,
          "lease_type": "전세",
          "area": 30.0,
          "floor": 10,
          "build_year": 2020,
          "final_score": 41.8146,
          "review_count": 5,
          "avg_rating": 4.0,
          "data_source": "USER",
          "status": "ACTIVE",
          "owned_by_current_user": false,
          "can_edit": true,
          "can_change_status": false
        }
      ],
      "averagePriceScore": 60.0,
      "averageSpaceScore": 50.0,
      "districtSafetyScore": 62.3146,
      "averageFinalScore": 62.3146,
      "representativeScore": 86.3865
    }
  ]
}
```

### 6.4 금지 상태 확인

| 검증 항목 | 기대값 | 실측값 | 판정 |
|-----------|--------|--------|------|
| Reader HTTP Status | 200 | 200 | PASS |
| 응답 내 TARGET_A 포함 여부 | 포함 | `"property_id": "F009_TARGET_A0000000000000000000"` | **PASS** |
| TARGET_A의 price | 80,000 | `"price": 80000` | **PASS** |
| 요청 budgetMax | 60,000 | 60,000 | - |
| **budgetMax < price 모순** | 60,000 < 80,000 | **확인** | **금지 상태 재현** |

**판정: budgetMax=60,000 조건의 추천 조회 응답에 deposit=80,000 매물이 포함되었다. F009 Write-Read 경합 재현 성공.**

### 6.5 Redis 상태 — 테스트 후 (최종 상태)

```
> HGET property:charter:F009_TARGET_A0000000000000000000 deposit
"\"80000\""

> ZSCORE idx:charterPrice:강남구 "\"F009_TARGET_A0000000000000000000\""
"80000"
```

**판정: Hash deposit=80,000, ZSet score=80,000 — Writer 해제 후 정합 상태 복원 완료.**

---

## 7. 실행 흐름 추적

### 7.1 타이밍 다이어그램

```
시간  Writer (PATCH 매물 수정)                      Reader (POST 추천 조회)
────  ────────────────────────────                 ────────────────────────────────
T0    요청 수신                                     Constant Timer 2000ms 대기 시작
      │                                            │
T1    findById → entity 로드 (deposit=50000)        │
T2    entity.setDeposit(80000L)                     │
T3    charterRepository.save() [RDB 반영]           │
T4    syncRedisAfterUpdate() 진입                    │
      │                                            │
T5    Hash putAll 완료                               │
      │  deposit = "80000" (신값)                    │
      │                                            │
T6    syncAwait() 진입 → await() 블로킹 ●             │
      │                                            │
      │  ┌─────────────────────────────┐           │
      │  │  Redis 중간 상태 (핵심)       │           │
      │  │  Hash deposit = 80000 (신값) │           │
      │  │  ZSet score  = 50000 (구값)  │           │
      │  └─────────────────────────────┘           │
      │                                            │
T7    │ (대기 중)                                    Constant Timer 만료, 요청 수신
T8    │                                            strict search 시작 (25개 자치구 순회)
T9    │                                            [강남구] ZRANGEBYSCORE 가격 40000~60000
      │                                            → TARGET_A(50000) 포함 ← ZSet 구값 기준
T10   │                                            [강남구] ZRANGEBYSCORE 평수 20~40
      │                                            → TARGET_A(30) 포함
T11   │                                            retainAll → TARGET_A 후보 확정
      │                                            │
T12   │ ← countDown()                              releaseWriterIfTargetIncluded() 호출
      │                                            │
T13   await() 해제 ○                                나머지 자치구 처리 계속
T14   ZSet add score=80000                          │
T15   bounds 갱신                                    calculateCharterPropertyScores() 진입
T16   응답 반환 (HTTP 200)                            │
      │                                            HGETALL property:charter:TARGET_A
      │                                            → deposit = 80000 (신값) ← Hash 신값 읽음
T17                                                 점수 계산, 정렬
T18                                                 응답 반환 (HTTP 200)
                                                   → TARGET_A: price=80000 포함
```

### 7.2 핵심 구간 분석 (T6~T12)

T6에서 Writer가 블로킹된 순간부터 T12에서 Reader가 latch를 해제하는 순간까지, Redis는 다음 중간 상태를 유지한다.

```
Hash:  property:charter:F009_TARGET_A0000000000000000000  →  deposit = "80000" (신값)
ZSet:  idx:charterPrice:강남구                             →  score = 50000    (구값)
```

Reader는 T9에서 ZSet score=50,000을 기준으로 TARGET_A를 후보로 선정한다 (budgetMax=60,000 이내).
이후 T16에서 Hash deposit=80,000을 읽는다.

그 결과 조회 조건(budgetMax=60,000)과 응답 데이터(price=80,000)가 모순되는 금지 상태가 발생한다.

---

## 8. 근본 원인 분석

### 8.1 원인

`CharterPropertyWriteService.syncRedisAfterUpdate()`에서 Redis Hash 갱신과 ZSet 인덱스 갱신이 **개별 Redis 커맨드로 순차 실행**된다.

```java
// [1단계] Hash 전체 덮어쓰기 — deposit=80000 반영
redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

// ← 이 사이에 Reader가 끼어들 수 있는 시간 간극 존재

// [2단계] ZSet score 갱신 — deposit=80000 반영
redisHandler.redisTemplate.opsForZSet().add(
    "idx:charterPrice:" + districtName, propertyId,
    entity.getDeposit().doubleValue());
```

1단계와 2단계 사이에 Reader가 ZSet을 조회하면, ZSet의 구값 기준으로 후보를 선정하고 Hash의 신값을 읽게 된다.

### 8.2 영향

| 구분 | 내용 |
|------|------|
| 데이터 정합성 | 조회 조건과 응답 매물 가격이 모순된다 |
| 사용자 경험 | 예산 초과 매물이 추천 결과에 노출된다 |
| 발생 조건 | 매물 수정과 추천 조회가 동시에 실행되는 경우 |
| 발생 빈도 | Hash-ZSet 갱신 간 간극(수 ms)에 Reader가 진입하는 확률에 의존 |

### 8.3 해결 방향

Redis `MULTI/EXEC` 트랜잭션으로 Hash putAll과 ZSet add를 원자적으로 실행하여, Reader가 중간 상태를 관찰할 수 없도록 한다.

```
MULTI/EXEC 적용 후 허용 상태:

  [상태 A] Reader가 수정 전 전체 상태를 관찰
    → Hash deposit=50000, ZSet score=50000
    → TARGET_A가 후보에 포함되고 price=50000으로 응답 (정합)

  [상태 B] Reader가 수정 후 전체 상태를 관찰
    → Hash deposit=80000, ZSet score=80000
    → TARGET_A가 ZSet 범위(40000~60000)에서 탈락하여 후보에 미포함 (정합)
```

---

## 9. 테스트 코드 위치

| 파일 | 경로 | 역할 |
|------|------|------|
| F009RaceLatch | `com.wherehouse.test.F009RaceLatch` | Writer-Reader 동기화 래치 |
| Writer 훅 | `CharterPropertyWriteService.syncRedisAfterUpdate()` L501~L503 | Hash 갱신 후 `syncAwait()` 호출 |
| Reader 훅 | `CharterRecommendationService.findValidCharterPropertiesInDistrict()` L261~L263 | ZSet 후보 확정 후 `releaseWriterIfTargetIncluded()` 호출 |
| JMeter 플랜 | `docs/.../F009/테스트파일/F009_WriteRead_Race_Test.jmx` | 테스트 시나리오 정의 |
| 테스트 데이터 | `docs/.../F009/테스트파일/F009_테스트데이터_주입_명령어.md` | SQL + Redis 주입 명령어 |

---

## 10. 비고

### 10.1 JMeter Assertion 불일치

JMeter JSONPath Assertion에서 `$.recommended_districts`로 경로를 지정하였으나, 실제 응답 필드명은 `$.recommendedDistricts`(camelCase)이다.
이로 인해 Assertion이 경로 불일치로 FAIL 처리되었으나, 응답 본문을 직접 확인하여 금지 상태(TARGET_A, price=80,000)가 재현되었음을 수동 검증하였다.

### 10.2 테스트 1회성 제약

`F009RaceLatch`는 `CountDownLatch`(1회성)를 사용하므로, 테스트 반복 시 애플리케이션 재시작이 필요하다.
재시작 시 `@PostConstruct`에서 latch가 자동 재초기화된다.

### 10.3 테스트 중 인위적 딜레이 배제

테스트 데이터 주입 명령서(섹션 13-2)에 기술된 Redis CLI 중간 상태 수동 관찰 절차는 본 테스트에서 수행하지 않았다.
동시성 경합 테스트에서 인위적 딜레이를 주입하면 실제 경합 조건을 왜곡할 수 있으므로, `F009RaceLatch`의 구조적 동기화만으로 경합을 재현하였다.

Reader의 Constant Timer 2,000ms는 Writer가 latch 도달점까지 실행을 완료하기 위한 최소 마진이며, 중간 상태 관찰용 딜레이가 아니다.

---

## 11. 결론

본 테스트는 Postman/JMeter 요청 타이밍에 의존하지 않고, Writer의 Redis Hash 갱신 직후와 ZSet 갱신 직전 사이를 `CountDownLatch`로 고정하였다.

이후 추천 조회 API가 기존 서비스 흐름 그대로 가격 ZSet과 평수 ZSet의 교집합 후보를 확정하게 한 뒤, Writer를 재개시켜 ZSet을 갱신하도록 구성하였다.

그 결과 추천 조회 API는 구 ZSet 기준(score=50,000)으로 후보를 선정하고, 이후 신 Hash 값(deposit=80,000)을 읽어 **budgetMax=60,000 조건에서 deposit=80,000 매물을 응답에 포함하였다.**

이는 Redis Hash와 ZSet을 개별 커맨드로 순차 갱신하는 현재 구조에서 Reader가 Writer의 중간 상태를 관찰할 수 있음을 실증한다.

**후속 조치: Redis `MULTI/EXEC` 트랜잭션을 적용하여 Hash/ZSet 갱신의 원자성을 확보하고, 동일 시나리오에서 금지 상태가 사라지는지 검증한다.**
