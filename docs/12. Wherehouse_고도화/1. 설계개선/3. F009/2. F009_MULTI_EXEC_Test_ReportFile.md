# F009 MULTI/EXEC 적용 후 Write-Read 경합 재테스트 결과 보고서

| 항목 | 내용 |
|------|------|
| **문서 ID** | F009-TEST-REPORT-002 |
| **테스트 일시** | 2026-05-07 18:14 (KST) |
| **테스트 수행자** | bumjinDev |
| **테스트 도구** | Apache JMeter 5.6.3 |
| **테스트 대상** | Wherehouse 전세 매물 수정 API + 전세 추천 조회 API |
| **선행 테스트** | F009-TEST-REPORT-001 (개별 커맨드 경합 재현 — 금지 상태 확인) |
| **판정 결과** | **MULTI/EXEC 쓰기 원자성 확보, 그러나 Reader 2단계 읽기로 인해 금지 상태 여전히 재현** |

---

## 1. 테스트 목적

선행 테스트(F009-TEST-REPORT-001)에서 Redis Hash와 ZSet을 개별 커맨드로 순차 갱신하는 구조가 Write-Read 경합의 근본 원인임을 확인하였다.

본 테스트는 `syncRedisAfterUpdate()` 메서드에 Redis `MULTI/EXEC` 트랜잭션을 적용하여 Hash putAll과 ZSet add를 원자적으로 실행한 후, 동일한 JMeter 시나리오에서 금지 상태(budgetMax=60,000 < price=80,000 매물 포함)가 사라지는지 검증한다.

---

## 2. 변경 사항 — MULTI/EXEC 적용

### 2.1 변경 전 (개별 커맨드 순차 실행)

```java
// CharterPropertyWriteService.syncRedisAfterUpdate() — 변경 전

private void syncRedisAfterUpdate(PropertyCharterEntity entity, List<String> changedFields) {
    String propertyId = entity.getPropertyId();
    String districtName = entity.getDistrictName();

    String hashKey = "property:charter:" + propertyId;
    Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);

    // [개별 커맨드 1] Hash 전체 덮어쓰기
    redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

    // F009 테스트 훅
    if (f009RaceLatch != null) {
        f009RaceLatch.syncAwait(propertyId);
    }

    // [개별 커맨드 2] ZSet score 갱신
    if (changedFields.contains("deposit")) {
        redisHandler.redisTemplate.opsForZSet().add(
                "idx:charterPrice:" + districtName, propertyId,
                entity.getDeposit().doubleValue());
        String boundsKey = "bounds:" + districtName + ":전세";
        boundsUpdater.tryExtend(boundsKey, "minPrice", "maxPrice",
                entity.getDeposit().doubleValue(), PRICE_ZERO_DELTA);
    }
}
```

**문제점**: Hash putAll과 ZSet add가 개별 Redis 커맨드로 실행되므로, 두 커맨드 사이에 Reader가 끼어들어 중간 상태(Hash=신값, ZSet=구값)를 관찰할 수 있다.

### 2.2 변경 후 (MULTI/EXEC 트랜잭션)

```java
// CharterPropertyWriteService.syncRedisAfterUpdate() — 변경 후

@SuppressWarnings("unchecked")
private void syncRedisAfterUpdate(PropertyCharterEntity entity, List<String> changedFields) {
    String propertyId = entity.getPropertyId();
    String districtName = entity.getDistrictName();

    String hashKey = "property:charter:" + propertyId;
    Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);

    redisHandler.redisTemplate.execute(new SessionCallback<List<Object>>() {
        @Override
        public List<Object> execute(RedisOperations operations) throws DataAccessException {
            operations.multi();

            operations.opsForHash().putAll(hashKey, hashFields);

            // F009 테스트 훅: MULTI 내부이므로 명령은 QUEUED 상태
            if (f009RaceLatch != null) {
                f009RaceLatch.syncAwait(propertyId);
            }

            if (changedFields.contains("deposit")) {
                operations.opsForZSet().add(
                        "idx:charterPrice:" + districtName, propertyId,
                        entity.getDeposit().doubleValue());
                String boundsKey = "bounds:" + districtName + ":전세";
                boundsUpdater.tryExtend(boundsKey, "minPrice", "maxPrice",
                        entity.getDeposit().doubleValue(), PRICE_ZERO_DELTA);
            }

            return operations.exec();
        }
    });
}
```

**변경 핵심**: `SessionCallback` 내에서 `operations.multi()` → 모든 Redis 명령 QUEUED → `operations.exec()`로 원자적 실행. Hash putAll, ZSet add, bounds 갱신이 모두 MULTI/EXEC 블록 내에 포함된다.

### 2.3 MULTI/EXEC 동작 원리

```
┌─────────────────────────────────────────────────────────┐
│ operations.multi()                                       │
│   → Redis 연결이 MULTI 모드로 진입                        │
│                                                          │
│ operations.opsForHash().putAll(hashKey, hashFields)       │
│   → QUEUED (실행되지 않음, Redis 상태 변경 없음)            │
│                                                          │
│ f009RaceLatch.syncAwait(propertyId)                       │
│   → Writer 블로킹 (이 시점 Redis 실제 데이터는 전부 구값)    │
│                                                          │
│ operations.opsForZSet().add(...)                          │
│   → QUEUED (실행되지 않음, Redis 상태 변경 없음)            │
│                                                          │
│ operations.exec()                                        │
│   → QUEUED된 모든 명령이 원자적으로 실행                    │
│   → Hash=80000, ZSet=80000 동시에 반영                    │
└─────────────────────────────────────────────────────────┘
```

**개별 커맨드 방식과의 차이**: 개별 커맨드에서는 `putAll()` 시점에 Hash가 즉시 80,000으로 변경되었으나, MULTI/EXEC에서는 `exec()` 호출 전까지 모든 명령이 QUEUED 상태로 Redis 실제 데이터에 반영되지 않는다.

---

## 3. 테스트 환경

선행 테스트(F009-TEST-REPORT-001)와 동일한 환경을 사용하였다. 변경 사항은 `syncRedisAfterUpdate()` 메서드의 MULTI/EXEC 적용뿐이다.

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
| MULTI/EXEC | **적용** | `SessionCallback` 기반 |

### 3.3 테스트 데이터

선행 테스트와 동일한 3건의 매물 데이터를 사용하였다. 테스트 전 애플리케이션 재시작으로 RdbSyncListener가 RDB→Redis 자동 동기화를 수행하여 초기 상태를 복원하였다.

| Property ID | 역할 | 초기 Deposit | District |
|-------------|------|-------------|----------|
| `F009_TARGET_A0000000000000000000` | 수정 대상 | 50,000 | 강남구 |
| `F009_DUMMY_B00000000000000000000` | strict search 충족용 | 52,000 | 강남구 |
| `F009_DUMMY_C00000000000000000000` | strict search 충족용 | 54,000 | 강남구 |

---

## 4. JMeter 테스트 구성

선행 테스트와 동일한 JMeter 플랜을 사용하였다.

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

| 항목 | Writer | Reader |
|------|--------|--------|
| Thread 수 | 1 | 1 |
| Loop Count | 1 | 1 |
| Connect Timeout | 5,000ms | 5,000ms |
| Response Timeout | 60,000ms | 60,000ms |
| Startup Delay | 0초 | 0초 (Constant Timer 2,000ms) |

---

## 5. 테스트 실행 결과

### 5.1 Redis 상태 — 테스트 전 (초기 상태)

```
> HGET property:charter:F009_TARGET_A0000000000000000000 deposit
"\"50000\""

> ZSCORE idx:charterPrice:강남구 "\"F009_TARGET_A0000000000000000000\""
"50000"
```

**판정: Hash deposit=50,000, ZSet score=50,000 — 정합 상태 확인.**

### 5.2 Writer 응답

| 항목 | 값 |
|------|-----|
| HTTP Status | 200 OK |
| Content-Type | application/json |
| 로드 시간 | 2,086ms |
| 대기 시간 | 2,084ms |
| Date | Thu, 07 May 2026 09:14:24 GMT |

```json
{
  "property_id": "F009_TARGET_A0000000000000000000",
  "modified_at": "2026-05-07T18:14:23",
  "changed_fields": ["deposit"]
}
```

**판정: deposit 필드 수정 정상 완료. 로드 시간 2,086ms는 latch 대기 시간을 포함한 값이다.**

### 5.3 Reader 응답

| 항목 | 값 |
|------|-----|
| HTTP Status | 200 OK |
| Content-Type | application/json |
| 로드 시간 | 76ms |
| 대기 시간 | 76ms |
| Date | Thu, 07 May 2026 09:14:24 GMT |

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
          "property_id": "F009_DUMMY_C0000000000000000000",
          "property_name": "F009 테스트아파트 C",
          "address": "서울특별시 강남구 역삼동 100-3",
          "price": 54000,
          "lease_type": "전세",
          "area": 32.0,
          "floor": 12,
          "build_year": 2020,
          "final_score": 76.8146,
          "review_count": 5,
          "avg_rating": 4.0
        },
        {
          "property_id": "F009_DUMMY_B0000000000000000000",
          "property_name": "F009 테스트아파트 B",
          "address": "서울특별시 강남구 역삼동 100-2",
          "price": 52000,
          "lease_type": "전세",
          "area": 31.0,
          "floor": 11,
          "build_year": 2020,
          "final_score": 74.3146,
          "review_count": 5,
          "avg_rating": 4.0
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
          "final_score": 71.8146,
          "review_count": 5,
          "avg_rating": 4.0,
          "data_source": "USER",
          "status": "ACTIVE"
        }
      ],
      "averagePriceScore": 100.0,
      "averageSpaceScore": 50.0,
      "districtSafetyScore": 74.3146,
      "averageFinalScore": 74.3146,
      "representativeScore": 103.0220
    }
  ]
}
```

### 5.4 금지 상태 확인

| 검증 항목 | 기대값 | 실측값 | 판정 |
|-----------|--------|--------|------|
| Reader HTTP Status | 200 | 200 | PASS |
| 응답 내 TARGET_A 포함 여부 | 미포함 (기대) | `"property_id": "F009_TARGET_A0000000000000000000"` | **포함됨** |
| TARGET_A의 price | — | `"price": 80000` | — |
| 요청 budgetMax | 60,000 | 60,000 | — |
| **budgetMax < price 모순** | 발생하지 않아야 함 | **60,000 < 80,000 확인** | **금지 상태 여전히 재현** |

**판정: MULTI/EXEC 적용 후에도 budgetMax=60,000 조건의 추천 조회 응답에 deposit=80,000 매물이 포함되었다.**

### 5.5 Redis 상태 — 테스트 후 (최종 상태)

```
> HGET property:charter:F009_TARGET_A0000000000000000000 deposit
"\"80000\""

> ZSCORE idx:charterPrice:강남구 "\"F009_TARGET_A0000000000000000000\""
"80000"
```

**판정: Hash deposit=80,000, ZSet score=80,000 — EXEC에 의해 양쪽 모두 원자적으로 갱신 완료.**

---

## 6. 애플리케이션 콘솔 로그 분석

### 6.1 전체 로그 (시간순)

```
18:14:22.904 [exec-1] Initializing Spring DispatcherServlet 'dispatcherServlet'
18:14:22.906 [exec-1] Completed initialization in 2 ms
18:14:22.926 [exec-1] JWTAuthenticationFilter - 요청 처리 시작
18:14:22.927 [exec-1] JWTAuthenticationFilter - HTTP Method: PATCH, URL: .../F009_TARGET_A0000000000000000000
18:14:22.967 [exec-1] JWTAuthenticationFilter - JWT 인증 성공: 사용자 ID = yhjj815
18:14:23.053 [exec-1] PropertyWriteController - 전세 매물 수정 요청: userId=yhjj815, propertyId=F009_TARGET_A0000000000000000000

18:14:24.871 [exec-2] JWTAuthenticationFilter - 요청 처리 시작
18:14:24.871 [exec-2] JWTAuthenticationFilter - HTTP Method: POST, URL: .../recommendations/charter-districts
18:14:24.875 [exec-2] JWTAuthenticationFilter - JWT 인증 성공: 사용자 ID = yhjj815
18:14:24.891 [exec-2] RecommendationController - === 전세 지역구 추천 요청 시작 (POST) ===
18:14:24.891 [exec-2] RecommendationController - 요청 파라미터: budgetMax=60000, ...
18:14:24.894 [exec-2] CharterRecommendationService - === 전세 지역구 추천 서비스 시작 ===
18:14:24.894 [exec-2] CharterRecommendationService - S-01: 전세 매물 검색 시작 - 대상: 25

Latch 해제                          ← Reader: releaseWriterIfTargetIncluded() 호출
Latch 걸기                          ← Writer: syncAwait() 리턴
Latch 해제 후 실제 데이터 수정 시작    ← Writer: MULTI 내 나머지 명령 QUEUE + EXEC 진행

18:14:24.915 [exec-1] CharterPropertyWriteService - 전세 매물 Redis 동기화(수정) 완료
18:14:24.915 [exec-1] PropertyWriteController - 전세 매물 수정 완료: propertyId=F009_TARGET_A0000000000000000000

18:14:24.926 [exec-2] CharterRecommendationService - S-05: 지역구 점수 계산 및 정렬 시작
18:14:24.931 [exec-2] RecommendationController - === 전세 지역구 추천 요청 완료 - 상태: SUCCESS_NORMAL ===
```

### 6.2 타이밍 다이어그램

```
시간           Writer (exec-1, PATCH)                     Reader (exec-2, POST)
─────          ──────────────────────                     ───────────────────────────
18:14:22.927   PATCH 요청 수신                              (JMeter Constant Timer 대기 중)
18:14:23.053   매물 수정 처리 시작                            │
               │                                           │
               MULTI 진입                                   │
               Hash putAll → QUEUED                        │
               syncAwait() → 블로킹 ●                       │
               │                                           │
               │  ┌───────────────────────────────────┐    │
               │  │  Redis 실제 상태 (MULTI 내부)       │    │
               │  │  Hash deposit = 50000 (구값)       │    │
               │  │  ZSet score   = 50000 (구값)       │    │
               │  │  (putAll은 QUEUED, 미실행)          │    │
               │  └───────────────────────────────────┘    │
               │                                           │
18:14:24.871   │                                           POST 요청 수신
18:14:24.894   │                                           S-01: 25개 구 순회 시작
               │                                           │
               │                                           [강남구] ZRANGEBYSCORE 가격 40000~60000
               │                                           → TARGET_A(50000) 포함 ← 구값
               │                                           [강남구] ZRANGEBYSCORE 평수 20~40
               │                                           → TARGET_A(30) 포함
               │                                           retainAll → TARGET_A 후보 확정
               │                                           │
18:14:24.894   │ ← countDown()                             releaseWriterIfTargetIncluded()
               │                                           │ "Latch 해제"
               │                                           │
               await() 해제 ○  "Latch 걸기"                 │
               ZSet add → QUEUED                           │ (나머지 구 순회 계속)
               boundsUpdater → 실행                         │
               │                                           │
18:14:24.915   exec() → Hash=80000, ZSet=80000 원자 커밋    │
               응답 반환 (HTTP 200)                          │
               │                                           │
18:14:24.926                                                S-05: 점수 계산 시작
                                                           │
                                                           Hash HGETALL TARGET_A
                                                           → deposit = 80000 ← EXEC 이후이므로 신값
                                                           │
18:14:24.931                                                응답 반환 (HTTP 200)
                                                           → TARGET_A: price=80000 포함
```

### 6.3 핵심 타이밍 분석

| 이벤트 | 시각 | 간격 |
|--------|------|------|
| Reader가 latch 해제 | 18:14:24.894 | — |
| Writer EXEC 완료 (Hash+ZSet 원자 커밋) | 18:14:24.915 | latch 해제 후 **+21ms** |
| Reader Hash 읽기 시작 (S-05) | 18:14:24.926 | latch 해제 후 **+32ms** |

Writer의 EXEC가 Reader의 Hash 읽기보다 **11ms 먼저 완료**되었다. Reader는 EXEC 이후의 신값(deposit=80,000)을 읽었다.

---

## 7. 개별 커맨드 vs MULTI/EXEC — 비교 분석

### 7.1 개별 커맨드 (선행 테스트, F009-TEST-REPORT-001)

```
Writer 실행 순서:
  [1] Hash putAll       → 즉시 실행, deposit=80000 반영
  [2] syncAwait()       → 블로킹
  [3] ZSet add          → latch 해제 후 실행, score=80000 반영

Reader가 관찰한 상태:
  ZSet score  = 50000 (구값, [3] 미실행)
  Hash deposit = 80000 (신값, [1] 이미 실행)

금지 상태 원인: Hash와 ZSet 사이에 실제 중간 상태(Hash≠ZSet)가 존재
```

### 7.2 MULTI/EXEC (본 테스트)

```
Writer 실행 순서:
  [1] multi()           → MULTI 모드 진입
  [2] Hash putAll       → QUEUED (미실행)
  [3] syncAwait()       → 블로킹 (이 시점 Redis 실제 데이터 전부 구값)
  [4] ZSet add          → QUEUED (미실행)
  [5] exec()            → [2]+[4] 원자적 실행, Hash=80000+ZSet=80000 동시 반영

Reader가 관찰한 상태:
  ZSet score  = 50000 (latch 블로킹 중 읽음 → EXEC 전이므로 구값)
  Hash deposit = 80000 (S-05 단계에서 읽음 → EXEC 후이므로 신값)

금지 상태 원인: Hash≠ZSet 중간 상태는 없었으나,
              Reader의 2단계 읽기(ZSet → Hash)가 EXEC 경계를 걸침
```

### 7.3 핵심 차이 요약

| 비교 항목 | 개별 커맨드 | MULTI/EXEC |
|-----------|-----------|------------|
| Hash putAll 실행 시점 | 즉시 (latch 전) | EXEC 시점 (latch 후) |
| ZSet add 실행 시점 | latch 해제 후 개별 실행 | EXEC 시점 (Hash와 동시) |
| latch 블로킹 중 Redis 상태 | Hash=80000, ZSet=50000 **(불일치)** | Hash=50000, ZSet=50000 **(일치, 구값)** |
| Hash-ZSet 중간 상태 존재 여부 | **존재** | **미존재** |
| 금지 상태 발생 원인 | Writer 쓰기 중간 상태 노출 | Reader 읽기가 EXEC 경계 걸침 |

---

## 8. MULTI/EXEC 효과 및 한계

### 8.1 MULTI/EXEC가 해결하는 문제

```
해결됨: Writer의 쓰기 원자성

  변경 전: Hash putAll → (중간 상태) → ZSet add
           Reader가 중간 상태를 관찰 가능

  변경 후: MULTI → Hash putAll QUEUED → ZSet add QUEUED → EXEC (원자적 실행)
           Hash≠ZSet 중간 상태가 존재하지 않음

  프로덕션 환경(latch 없음)에서:
    MULTI → 모든 명령 QUEUED → EXEC
    전체 소요 시간: 수 마이크로초
    이 윈도우에 Reader가 끼어들 확률: 극히 낮음
```

### 8.2 MULTI/EXEC가 해결하지 못하는 문제

```
미해결: Reader의 2단계 읽기 일관성

  Reader 흐름:
    [1단계] ZSet ZRANGEBYSCORE → 후보 ID 선정
    [시간 경과: 25개 구 순회 + 점수 계산]
    [2단계] Hash HGETALL → 매물 상세 조회

  1단계와 2단계 사이에 Writer EXEC가 실행되면:
    1단계: ZSet 구값 기준으로 TARGET_A 후보 선정 (score=50000 ≤ budgetMax=60000)
    2단계: Hash 신값 읽기 (deposit=80000)
    → budgetMax=60000 < price=80000 모순

  이 문제는 Writer가 아닌 Reader 측의 구조적 특성이다.
  Writer의 쓰기가 아무리 원자적이어도, Reader의 읽기가 원자적이지 않으면 발생한다.
```

### 8.3 테스트 환경 vs 프로덕션 환경

| 구분 | 테스트 환경 | 프로덕션 환경 |
|------|-----------|-------------|
| latch | 있음 (MULTI 윈도우 인위 확장) | 없음 |
| MULTI→EXEC 사이 시간 | 수 초 (latch 대기) | 수 마이크로초 |
| EXEC 경계를 Reader가 걸칠 확률 | 100% (latch 설계) | 극히 낮음 |
| Hash≠ZSet 중간 상태 | 미존재 (QUEUED) | 미존재 (QUEUED) |

프로덕션에서 MULTI/EXEC는 Hash≠ZSet 중간 상태를 완전히 제거하며, EXEC 윈도우가 수 마이크로초이므로 Reader가 EXEC 경계를 걸칠 확률은 사실상 무시할 수 있다. 그러나 이론적으로 2단계 읽기 구조가 존재하는 한 완전한 보장은 불가능하다.

---

## 9. 근본 원인 재정의

### 9.1 1차 원인 (선행 테스트에서 확인)

Writer가 Hash와 ZSet을 개별 커맨드로 순차 실행하여 **쓰기 중간 상태(Hash≠ZSet)**가 노출된다.

→ **MULTI/EXEC로 해결됨.**

### 9.2 2차 원인 (본 테스트에서 확인)

Reader가 ZSet 인덱스 조회(후보 선정)와 Hash 데이터 조회(상세 정보)를 **비원자적으로 2단계에 걸쳐 수행**한다. 이 두 단계 사이에 Writer의 EXEC가 완료되면, Reader는 구값 기준으로 선정한 후보에 대해 신값 데이터를 읽는다.

```
Reader의 2단계 읽기 구조:

  CharterRecommendationService.findValidCharterPropertiesInDistrict()
    → ZSet ZRANGEBYSCORE로 가격/면적 후보 선정 (1단계)

  CharterRecommendationService.getMultipleCharterPropertiesFromRedis()
    → Hash HGETALL Pipeline으로 상세 데이터 조회 (2단계)

  두 메서드 사이에 25개 구 순회 + 점수 계산이 존재하여,
  1단계와 2단계의 시간 간격이 수십 ms에 달한다.
```

→ **Reader 측 후검증(Post-fetch Validation)으로 해결 필요.**

---

## 10. 해결 방향

### 10.1 방어 계층 설계

| 계층 | 위치 | 역할 | 해결하는 문제 |
|------|------|------|-------------|
| **1계층: Writer MULTI/EXEC** | `syncRedisAfterUpdate()` | Hash+ZSet 쓰기 원자성 확보 | 쓰기 중간 상태(Hash≠ZSet) 제거 |
| **2계층: Reader 후검증** | `getMultipleCharterPropertiesFromRedis()` 이후 | Hash 실측값이 원래 조건에 부합하는지 재검증 | 2단계 읽기의 EXEC 경계 걸침 방어 |

### 10.2 Reader 후검증 개념

```
현재 흐름:
  [1단계] ZSet → 후보 ID: {TARGET_A, DUMMY_B, DUMMY_C}
  [2단계] Hash → TARGET_A.deposit=80000, DUMMY_B.deposit=52000, DUMMY_C.deposit=54000
  [3단계] 응답 반환 → TARGET_A(price=80000) 포함 ← 금지 상태

후검증 적용 후:
  [1단계] ZSet → 후보 ID: {TARGET_A, DUMMY_B, DUMMY_C}
  [2단계] Hash → TARGET_A.deposit=80000, DUMMY_B.deposit=52000, DUMMY_C.deposit=54000
  [3단계] 후검증: deposit(80000) > budgetMax(60000) → TARGET_A 제거
  [4단계] 응답 반환 → {DUMMY_B, DUMMY_C}만 포함 ← 정합 상태
```

---

## 11. 비고

### 11.1 JMeter Assertion 불일치

선행 테스트와 동일하게, JMeter JSONPath Assertion에서 `$.recommended_districts`로 경로를 지정하였으나 실제 응답 필드명은 `$.recommendedDistricts`(camelCase)이다. Assertion FAIL은 이 경로 불일치에 의한 것이며, 금지 상태 판정은 응답 본문 수동 검증으로 수행하였다.

### 11.2 테스트 1회성 제약

선행 테스트와 동일하게, `CountDownLatch`는 1회성이므로 테스트 반복 시 애플리케이션 재시작이 필요하다.

### 11.3 Writer 로드 시간 차이

| 테스트 | Writer 로드 시간 | 설명 |
|--------|----------------|------|
| 개별 커맨드 (선행) | ~2,000ms | latch 대기 포함 |
| MULTI/EXEC (본 테스트) | 2,086ms | latch 대기 + MULTI/EXEC 오버헤드 포함 |

MULTI/EXEC의 추가 오버헤드는 무시할 수 있는 수준이다.

---

## 12. 결론

Redis `MULTI/EXEC` 트랜잭션 적용은 Writer의 **쓰기 원자성**을 확보하여 Hash≠ZSet 중간 상태를 제거하는 데 성공하였다. MULTI 블로킹 구간에서 Redis 실제 데이터는 전부 구값(Hash=50,000, ZSet=50,000)을 유지하며, EXEC 시점에 양쪽이 동시에 80,000으로 갱신된다.

그러나 본 테스트에서 금지 상태가 여전히 재현되었다. 원인은 Writer가 아닌 **Reader의 구조적 특성**이다. Reader는 ZSet 인덱스 조회(후보 선정)와 Hash 데이터 조회(상세 정보)를 시간 간격을 두고 2단계로 수행하며, 이 간격 사이에 Writer의 EXEC가 완료되면 구값 기준으로 선정한 후보에 대해 신값을 읽게 된다.

이를 종합하면 F009 문제의 완전한 해결을 위한 방안을 고려해야 될 듯 싶다
