# F008: RDB 커밋 이후 Redis 동기화 보상 아키텍처 (전세 매물 생성)

**작성 기간:** 2026-04-28 ~ 2026-04-29 (통합 정리 2026-09-08)  
**대상 코드:** `com.wherehouse.PropertyManagement.service.CharterPropertyWriteService.createProperty()`  
**관련 코드:** `PropertySyncRetryScheduler`, `PropertySyncFailure`, `PropertySyncFailureRepository`  
**범위:** 전세 매물 등록(`createProperty()`) 한 곳. 월세 매물과 수정·상태 변경은 아직 적용하지 않음

---

## 목차

1. 요약
2. 문제 정의
3. 문제 재현 테스트
4. 첫 번째 결정: Redis 동기화를 Oracle commit 이후로 옮긴다
5. 두 번째 결정: commit 이후 Redis 실패는 기록하고 스케줄러가 복구한다
6. 구현
7. 트러블슈팅: afterCommit 내부 실패 기록이 DB에 남지 않는 문제
8. 검증 결과
9. 결론
10. 부록

---

## 1. 요약

Oracle RDB와 Redis를 함께 갱신하는 전세 매물 등록에서, Redis는 Oracle 트랜잭션의 rollback 대상이 아니다. 따라서 Redis 쓰기는 Oracle commit 이후에 실행해야 하고, commit 이후의 Redis 실패는 별도의 기록·재시도·삭제 구조로 관리해야 한다.

이번 구현이 전세 매물 등록에서 보장하는 것:

1. Oracle commit 전에는 Redis에 쓰지 않는다. `afterCommit` 콜백으로 보장한다.
2. Oracle rollback 시 Redis 동기화를 실행하지 않는다. 콜백 자체가 실행되지 않는다.
3. Oracle commit 이후 Redis 실패는 `PROPERTY_SYNC_FAILURES` 테이블에 기록한다. `REQUIRES_NEW` 독립 트랜잭션으로 저장한다.
4. 일시적 장애는 즉시 재시도 1회 + 스케줄러 재시도(1분 주기)로 복구한다.
5. 재시도 상한(기본 5회) 초과 시 Oracle 매물을 삭제하여 불일치 상태를 종료한다.

최종 상태는 두 가지 중 하나로 정리된다. Oracle 있음 + Redis 있음(성공), 또는 Oracle 없음 + Redis 없음(최종 실패). Oracle에만 있거나 Redis에만 있는 상태는 무한히 지속되지 않는다.

---

## 2. 문제 정의

### 2.1. 기존 흐름

```text
createProperty() 시작 (@Transactional)
  → Oracle INSERT SQL 전송
  → syncRedisAfterCreate(entity)   // Redis Hash + ZSet×2 + bounds×2
  → 응답 반환
  → 메서드 종료 시 commit
```

Redis 동기화가 `@Transactional` 경계 안에서, Oracle commit 확정 전에 실행된다.

### 2.2. Redis 동기화의 5개 명령

`syncRedisAfterCreate()`는 다음 5개 명령을 순차 실행한다. 각각 독립된 Redis 명령이므로 어느 지점에서든 네트워크 장애, 타임아웃, Redis 서버 중지, OOM으로 이후 명령이 실행되지 않을 수 있다.

```text
① Hash 저장        property:charter:{propertyId}         매물 상세 데이터
② 가격 ZSet 추가   idx:charterPrice:{districtName}       추천 검색 가격 인덱스
③ 면적 ZSet 추가   idx:area:{districtName}:전세          추천 검색 평수 인덱스
④ 가격 bounds 확장 bounds:{districtName}:전세 (minPrice/maxPrice)
⑤ 면적 bounds 확장 bounds:{districtName}:전세 (minArea/maxArea)
```

### 2.3. 사용자 조회 경로와 불일치 상태

매물 등록 후 사용자가 매물을 확인하는 경로는 두 가지다.

- 매물 목록·관리 조회(F004): Oracle `PROPERTIES_CHARTER` 직접 조회.
- 추천 검색: Redis Sorted Set 교집합. Oracle에 매물이 있어도 ZSet에 없으면 결과에 나오지 않는다.

이 구조에서 다음 상태는 모두 비즈니스 문제다.

| 상태 | 문제 |
|---|---|
| Oracle에 ACTIVE 매물 있음, Redis 없음 | 목록에는 보이지만 추천 검색에서 누락 |
| Oracle에 ACTIVE 매물 있음, Redis Hash만 있음 | 상세 조회는 되지만 추천 검색에서 누락 |
| Oracle에 없음, Redis Hash 남아 있음 | DB에 없는 데이터가 Redis에 남음 |
| Redis 인덱스 일부만 반영 | 검색 조건에 따라 노출 여부가 달라짐 |

### 2.4. F008이 지켜야 할 조건

```text
1. Oracle에 commit되지 않은 매물은 Redis에 반영되면 안 된다.
2. Oracle에 commit된 매물의 Redis 동기화가 실패하면 그 실패를 추적할 수 있어야 한다.
3. Redis 동기화 실패를 무한히 방치하면 안 된다.
4. 일시적 장애는 시스템이 자동 복구한다.
5. 장기 장애는 Oracle 매물까지 삭제하여 불일치 상태를 종료한다.
```

1번은 실행 순서 문제다. 2~5번은 보상 전략 문제다. 두 문제는 별개이므로 따로 결정한다(4장, 5장).

---

## 3. 문제 재현 테스트

### 3.1. 목적과 방법

기존 구조가 어떤 실패 상태를 만드는지 확인한다. `syncRedisAfterCreate()` 안에 `RuntimeException`을 넣어 장애를 재현하고, `@Transactional` 유무에 따라 Oracle과 Redis 상태를 비교한다.

| 시나리오 | 예외 주입 위치 | 확인하려는 상태 |
|---|---|---|
| 시나리오 1 (Redis 전체 실패) | `syncRedisAfterCreate()` 시작 직후 (Redis 명령 실행 전) | Redis 명령 5개가 하나도 실행되지 않음. Oracle에만 데이터가 있음 |
| 시나리오 2 (Redis 부분 실패) | ① Hash 저장 완료 후, ② ZSet 추가 직전 | 5개 중 ① Hash 저장만 실행되고 ②~⑤는 실행되지 않음. Redis에 Hash만 있음 |

이 문서에서 "전체 실패"는 Redis 명령 5개(2.2) 중 하나도 실행되지 않은 경우를 뜻한다. "부분 실패"는 ① Hash 저장까지만 실행되고 ② ZSet 추가부터 실행되지 않은 경우를 뜻한다.

시나리오 2에서 예외를 ①과 ② 사이에 넣은 이유는 이 지점의 불일치가 사용자에게 가장 크게 드러나기 때문이다. Hash는 상세 조회에 쓰이고 ZSet은 추천 검색에 쓰인다. Hash만 있고 ZSet이 없으면 상세 조회는 되는데 추천 검색에는 나오지 않는다.

5개 지점 전부에 예외를 넣지는 않았다. 어느 지점에서 실패하든 결과는 "그 시점 이후 명령이 실행되지 않음"으로 같아서, 지점마다 재현해도 추가로 확인되는 것이 없다.

테스트 환경은 부록 10.1에, 요청 본문은 10.2에 있다.

### 3.2. 예외 주입 코드

시나리오 1:

```java
private void syncRedisAfterCreate(PropertyCharterEntity entity) {
    if(true) { throw new RuntimeException("[F008-TEST] 테스트①: Redis 연결 실패 시뮬레이션"); }
    // 이하 Redis 작업 (미실행)
}
```

시나리오 2:

```java
private void syncRedisAfterCreate(PropertyCharterEntity entity) {
    String propertyId = entity.getPropertyId();
    String districtName = entity.getDistrictName();

    String hashKey = "property:charter:" + propertyId;
    Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);
    redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);   // ① Hash 저장 성공

    if(true) { throw new RuntimeException("[F008-TEST] 테스트②: Hash 저장 후 ZSet 추가 시점 실패 시뮬레이션"); }

    redisHandler.redisTemplate.opsForZSet().add(                           // ② ZSet 추가 (미실행)
            "idx:charterPrice:" + districtName, propertyId,
            entity.getDeposit().doubleValue());
    // ③④⑤ 이하 미실행
}
```

### 3.3. 동작 원리

`@Transactional`이 없으면 `charterRepository.save(entity)` 안에서 Spring Data JPA가 자체 트랜잭션을 열고 INSERT 직후 commit한다. `save()`가 끝나는 시점에 Oracle 데이터가 확정된다. 그 뒤에 예외가 발생해도 되돌릴 수 없다.

`@Transactional`이 있으면 메서드 시작 시 트랜잭션이 열리고 commit은 메서드가 정상 종료할 때까지 보류된다. 메서드 안에서 RuntimeException이 발생하면 Spring이 rollback한다.

rollback 대상은 JDBC Connection으로 보낸 SQL뿐이다. Redis 명령, Java 변수, 외부 시스템 호출은 rollback 대상이 아니다.

### 3.4. 결과

요청 본문(10.2)으로 생성되는 매물의 PROPERTY_ID는 `5c627892ec0c5bdd218bdd1a70d9f931`이다. 네 케이스 모두 같은 요청을 보냈고, 케이스가 끝날 때마다 Oracle 행은 DELETE로, Redis Hash는 `redis-cli DEL`로 삭제한 뒤 다음 케이스를 실행했다.

| 케이스 | 조건 | API 응답 | Oracle | Redis Hash | Redis ZSet | 상태 |
|---|---|---|---|---|---|---|
| 1-A | `@Transactional` 없음 + 전체 실패 (Redis 명령 5개 모두 실행 안 됨) | 500 | 1건 (ACTIVE, USER) | 없음 | 없음 | 불일치. Oracle에만 존재 |
| 1-B | `@Transactional` 있음 + 전체 실패 (Redis 명령 5개 모두 실행 안 됨) | 500 | 0건 (rollback) | 없음 | 없음 | 정합. 양쪽 모두 없음 |
| 2-A | `@Transactional` 없음 + 부분 실패 (① Hash 저장만 실행, ②~⑤ 실행 안 됨) | 500 | 1건 (ACTIVE) | 있음 | 없음 | 불일치. 상세 조회 가능, 추천 검색 누락 |
| 2-B | `@Transactional` 있음 + 부분 실패 (① Hash 저장만 실행, ②~⑤ 실행 안 됨) | 500 | 0건 (rollback) | 있음 | 없음 | 불일치. Oracle에 없는 Hash가 Redis에 남음 |

### 3.5. 케이스별 의미

케이스 1-A는 사용자가 다시 등록해도 해결되지 않는다. Oracle에 매물이 있으므로 같은 매물을 다시 등록하면 `findById`에서 `DuplicatePropertyException`이 발생한다. 사용자는 "이미 등록된 매물"이라는 응답을 받는다. 그런데 추천 검색에서는 그 매물을 찾을 수 없다.

케이스 1-B는 Oracle과 Redis 양쪽이 모두 비어 있으므로 정합성이 유지된다. 그러나 이 결과는 Redis 동기화가 RDB 트랜잭션 안에서 실행되어, Redis 실패가 RDB rollback을 일으켰기 때문에 나온 것이다. 다시 말해 RDB 저장은 성공할 수 있었는데 Redis 장애 하나가 매물 등록 전체를 막았다.

케이스 2-A는 1-A보다 복구가 어렵다. Redis에도 일부 데이터가 있으므로 어디까지 성공했는지 파악해야 복구할 수 있다.

케이스 2-B의 불일치가 가장 심하다. Oracle은 rollback되었는데 그 전에 실행된 Redis Hash 저장은 그대로 남는다. `@Transactional`이 Oracle만 되돌리고 Redis는 되돌리지 못해 불일치를 더 키운다.

### 3.6. 결론

> RDB commit이 확정되기 전에 Redis에 쓰기가 발생하면, `@Transactional`이 오히려 Redis/RDB 불일치를 악화시킨다.

1-B와 2-B는 둘 다 `@Transactional`을 적용했고 Oracle은 rollback되었다. 그런데 결과는 달랐다. 1-B는 Redis에 쓰기 전에 예외가 발생하여 양쪽이 비었고, 2-B는 Redis Hash를 쓴 뒤에 예외가 발생하여 Hash만 남았다. 다른 점은 예외가 발생한 시점 하나뿐이다. Redis 쓰기가 RDB commit 전에 실행되는 구조인 한, 예외가 언제 발생하느냐에 따라 2-B 같은 불일치가 언제든 발생한다.

여기서 결정할 것이 두 가지다. 첫째, Redis 쓰기를 RDB commit 확정 이후로 옮겨야 한다(4장). 둘째, 순서를 옮겨도 commit 이후에 Redis가 실패하는 경우는 남으므로, 그 실패를 처리할 보상 전략이 따로 필요하다(5장).

---

## 4. 첫 번째 결정: Redis 동기화를 Oracle commit 이후로 옮긴다

### 4.1. 목표

실행 순서를 다음과 같이 바꾼다.

```text
변경 전: Oracle INSERT → Redis 동기화 → 메서드 종료 → Oracle commit
변경 후: Oracle INSERT → 메서드 종료 → Oracle commit 확정 → Redis 동기화
```

순서를 바꾼다고 Redis 실패가 모두 해결되지는 않는다. 이 변경으로 막으려는 것은 하나다. Oracle에 commit되지 않은 데이터가 Redis에 반영되는 일이다.

### 4.2. 방법: afterCommit 콜백

서비스 메서드 안에서 Spring의 `TransactionSynchronization`을 등록하고, `afterCommit()`에 Redis 동기화를 넣는다.

```java
TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                syncRedisAfterCreate(entity);
            }
        }
);
```

이 콜백은 현재 트랜잭션이 commit된 뒤에 실행되고, rollback되면 실행되지 않는다. 그래서 Oracle commit 전에 Redis에 쓰는 경로가 사라진다. 기존 서비스 구조와 `syncRedisAfterCreate()` 내부는 바꾸지 않고 호출 시점만 옮긴다.

`afterCommit`은 "commit 전 Redis 쓰기" 문제만 해결한다. "commit 이후 Redis 실패"는 남으므로 두 번째 결정(5장)과 함께 적용해야 한다.

---

## 5. 두 번째 결정: commit 이후 Redis 실패는 기록하고 스케줄러가 복구한다

### 5.1. afterCommit으로 옮긴 뒤에도 남는 문제

```text
Oracle commit 성공
  → afterCommit에서 Redis 동기화 실행
  → Redis 서버 중지 또는 타임아웃
  → Oracle에는 매물 있음, Redis에는 없음
```

가능한 대응은 세 가지다.

1. 사용자에게 실패 응답을 반환한다.
2. 실패를 기록하고 자동 재시도한다.
3. 재시도 상한 초과 시 Oracle 매물까지 삭제한다.

2번과 3번을 둘 다 적용했다. 먼저 재시도하고, 재시도가 상한을 넘으면 삭제한다.

1번을 쓰지 않은 이유는 Redis 장애가 일시적일 수 있기 때문이다. 짧은 타임아웃이나 순간적인 연결 끊김은 몇 초 뒤 재시도하면 성공한다. 이런 경우까지 첫 실패에서 바로 실패 응답을 돌려주면, 재시도했으면 성공했을 등록이 실패로 끝난다.

실패 응답을 받은 사용자가 할 수 있는 것도 없다. Redis 장애는 사용자 입력과 무관하므로 다시 요청해도 같은 결과다. 그래서 사용자에게 실패 응답을 돌려주지 않고 시스템이 재시도하는 쪽을 택했다. 일시적 장애와 장기 장애를 나눠 처리하는 방법은 5.3에 있다.

### 5.2. 실패 기록 테이블

로그만 남기면 스케줄러가 재시도할 매물을 찾을 수 없다. 스케줄러가 조회하고 갱신할 수 있게 다음 정보를 컬럼으로 나눠 테이블에 저장한다.

| 컬럼 | 저장하는 정보 | 왜 저장하는가 |
|---|---|---|
| PROPERTY_ID | 어떤 매물인가 | 스케줄러가 이 값으로 Oracle에서 매물을 다시 읽어 Redis에 재동기화한다 |
| LEASE_TYPE | 전세인가 월세인가 | 해당 없음 |
| OPERATION_TYPE | 등록·수정·상태 변경 중 어느 작업이었나 | 해당 없음 |
| FAIL_STEP | Redis 명령 5개 중 어디서 실패했나 | 해당 없음 |
| RETRY_COUNT, MAX_RETRIES | 몇 번 재시도했고 몇 번까지 허용하나 | 스케줄러가 재시도에 실패할 때마다 RETRY_COUNT를 1 올리고, MAX_RETRIES에 도달하면 재시도를 멈추고 Oracle 매물을 삭제한다 |
| RESOLVED, RESOLVED_TIME | 해결되었는가, 언제 해결되었는가 | 스케줄러가 'N'인 레코드만 조회하므로, 이미 해결된 건을 다시 처리하지 않는다 |
| RESOLVED_METHOD | 어떻게 해결되었나 | 재동기화 성공이면 'RETRY', 삭제면 'ROLLBACK'을 남겨서 나중에 결과를 확인할 수 있게 한다 |

DDL은 부록 10.3에 있다. 전세 매물 등록(`CharterPropertyWriteService.createProperty()`)에서 Redis 동기화가 실패하면 `recordSyncFailure()`가 이 테이블에 레코드를 하나 넣는다. 그때 각 컬럼에 들어가는 값은 아래 표와 같다. 등록 외의 작업과 월세 매물은 아직 이 테이블을 쓰지 않는다.

| 컬럼 | 값 |
|---|---|
| LEASE_TYPE | 'CHARTER' 고정 |
| OPERATION_TYPE | 'CREATE' 고정 |
| FAIL_STEP | 'FULL' 고정 (Redis 명령 5개를 하나의 단위로 취급) |
| MAX_RETRIES | 5 |
| RESOLVED_METHOD | 스케줄러가 해결할 때 'RETRY' 또는 'ROLLBACK'으로 갱신 |

### 5.3. 즉시 재시도와 스케줄러 재시도

짧은 타임아웃이나 순간적인 연결 실패는 즉시 재시도로 해결될 수 있다. Redis 서버 중지, 네트워크 단절, OOM은 요청 스레드 안에서 반복해도 해결되지 않는다. 따라서 두 단계로 나눈다.

```text
1. afterCommit에서 Redis 동기화 1차 시도
2. 연결 실패·타임아웃 예외 → 200ms 대기 후 1회 재시도
3. 재시도 성공 → 실패 기록 없이 종료
4. 재시도 실패, 또는 인프라 장애(RedisSystemException)·예상하지 못한 예외 → PROPERTY_SYNC_FAILURES 기록
5. 스케줄러가 1분 주기로 미해결 레코드를 조회하여 재시도
6. 재시도 상한 초과 → Oracle 매물 삭제
```

### 5.4. 장기 장애 시 Oracle 삭제를 선택한 이유

선택지는 두 가지다.

1. Oracle 매물을 유지하고 Redis 복구를 계속 시도한다.
2. 상한 이후 Oracle 매물을 삭제하여 최종 실패로 정리한다.

2번을 선택했다. 이번 작업의 목표가 Oracle과 Redis의 불일치 상태를 무한히 방치하지 않는 구조를 만드는 것이기 때문이다. 상한 초과 시 Oracle 매물을 삭제하고 실패 레코드를 `RESOLVED='Y'`, `RESOLVED_METHOD='ROLLBACK'`으로 갱신한다.

실서비스라면 1번을 택하고 관리자 알림, 사용자 상태 표시, 재처리 큐, outbox pattern을 추가하는 것이 맞을 것이다.

---

## 6. 구현

### 6.1. 변경·신규 파일

| 유형 | 파일 | 위치 | 작업 |
|---|---|---|---|
| DDL | PROPERTY_SYNC_FAILURES | Oracle | 신규 테이블 |
| Entity | `PropertySyncFailure.java` | `entity/` | 신규 |
| Repository | `PropertySyncFailureRepository.java` | `repository/` | 신규. `findByResolvedOrderByFailTimeAsc(String)` 하나 |
| Scheduler | `PropertySyncRetryScheduler.java` | `scheduler/` (신규 패키지) | 신규 |
| Service | `CharterPropertyWriteService.java` | `service/` | 변경 |

`CharterPropertyWriteService` 변경 내용:

| 항목 | 작업 |
|---|---|
| 필드 | `PropertySyncFailureRepository`, `PlatformTransactionManager` 추가 |
| `createProperty()` | `syncRedisAfterCreate(entity)` 직접 호출 → afterCommit 콜백 등록 |
| `executeRedisSyncWithCompensation()` | 신규 private. 예외 분류 + 즉시 재시도 1회 + 실패 기록 |
| `recordSyncFailure()` | 신규 private. `REQUIRES_NEW` 독립 트랜잭션으로 실패 테이블 INSERT |
| `retrySyncForCreate()` | 신규 public. 스케줄러 호출용 래퍼 |
| `syncRedisAfterCreate()` | 변경 없음 |

이번 작업에서 손대지 않은 파일은 다음과 같다. 변경 범위가 `CharterPropertyWriteService`와 신규 파일 3개에 그친다는 뜻이다.

- 서비스: `MonthlyPropertyWriteService`
- 컨트롤러: `PropertyWriteController`, `PropertyQueryController`
- DTO와 기존 Entity 전부
- 예외 처리: `GlobalExceptionHandlerProperty`
- Redis 연동 헬퍼: `BoundsUpdater`, `PropertyHashBuilder`

### 6.2. createProperty() 현재 코드

F008 관점에서 확인할 부분은 하나다. INSERT 직후에 Redis 동기화 메서드를 바로 호출하지 않고, afterCommit 콜백에 넣어 commit 뒤에 실행되도록 예약만 한다.

`createProperty()`는 다음 순서로 동작한다.

1. 자치구 코드를 검증하고, 시군구코드·지번·아파트명·층·면적을 조합한 MD5 해시로 `propertyId`를 만든다.
2. `findById`로 같은 `propertyId`가 이미 있는지 확인한다. 있으면 `DuplicatePropertyException`을 던진다.
3. 요청 값으로 `PropertyCharterEntity`를 만든다.
4. `entityManager.persist()` + `flush()`로 INSERT SQL을 Oracle에 보낸다. 같은 PK가 동시에 들어와 제약 위반이 나면 `DuplicatePropertyException`으로 바꿔 던진다.
5. afterCommit 콜백을 등록한다. 콜백 안에서 `executeRedisSyncWithCompensation(entity)`를 부른다.
6. 응답 DTO를 만들어 반환한다.

메서드가 반환되면 `@Transactional`이 commit하고, 그 직후 5번에서 등록한 콜백이 실행된다. 4번의 `flush()`는 SQL을 보내기만 할 뿐 commit이 아니므로, Redis 동기화는 4번 직후가 아니라 콜백 안에 있어야 한다.

```java
@Transactional
public PropertyCreateResponseDto createProperty(CharterCreateRequestDto dto, String userId) {

    validateDistrictCode(dto.getSggCd());

    String propertyId = idGenerator.generatePropertyId(
            dto.getSggCd(), dto.getJibun(), dto.getAptNm(),
            String.valueOf(dto.getFloor()), dto.getExcluUseAr().toPlainString());

    log.info("[B-PK] 진입 — thread={}, propertyId={}", Thread.currentThread().getName(), propertyId);

    // ── 1차 필터: 이미 커밋된 행이 존재하는 경우를 빠르게 거부 ──
    // 정합성 보장 역할이 아님. 대부분의 중복 요청을 INSERT 없이 차단하는 성능 필터.
    Optional<PropertyCharterEntity> existing = charterRepository.findById(propertyId);
    log.info("[B-PK] findById 결과 — thread={}, found={}", Thread.currentThread().getName(), existing.isPresent());
    if (existing.isPresent()) {
        PropertyCharterEntity existingEntity = existing.get();
        if (existingEntity.getDataSource() == DataSource.BATCH) {
            throw new DuplicatePropertyException(
                    "이 매물은 국토교통부 실거래 데이터에 이미 등록된 매물입니다. propertyId=" + propertyId);
        }
        throw new DuplicatePropertyException(
                "동일한 매물이 다른 사용자에 의해 이미 등록되어 있습니다. propertyId=" + propertyId);
    }

    log.info("매물 ID={}", propertyId);

    String districtName = resolveDistrictName(dto.getSggCd());
    BigDecimal areaInPyeong = toPyeong(dto.getExcluUseAr());
    String address = buildAddress(districtName, dto.getUmdNm(), dto.getJibun());
    LocalDateTime now = LocalDateTime.now();

    PropertyCharterEntity entity = PropertyCharterEntity.builder()
            .propertyId(propertyId)
            .aptNm(dto.getAptNm()).excluUseAr(dto.getExcluUseAr())
            .floor(dto.getFloor()).buildYear(dto.getBuildYear()).dealDate(dto.getDealDate())
            .deposit(Long.valueOf(dto.getDeposit()))
            .leaseType(LEASE_CHARTER_KOR)
            .umdNm(dto.getUmdNm()).jibun(dto.getJibun()).sggCd(dto.getSggCd())
            .address(address).areaInPyeong(areaInPyeong).districtName(districtName)
            .dataSource(DataSource.USER).status(PropertyStatus.ACTIVE)
            .registeredUserId(userId).registeredAt(now)
            .build();

    // ── 2차 안전망: 동시 등록 레이스 대응 — Oracle PK 제약 활용 ──
    // saveAndFlush(): 즉시 INSERT SQL 실행을 강제하여 PK 제약 위반을 이 try 블록 안에서 catch.
    // save()만 사용하면 Hibernate가 flush를 커밋 시점까지 지연하므로
    // @Transactional 프록시 레이어에서 예외가 발생하여 catch 불가.

    try {
        log.info("[B-PK] persist+flush 시도 — thread={}, propertyId={}",
                Thread.currentThread().getName(), propertyId);

        entityManager.persist(entity);
        entityManager.flush();

        log.info("[B-PK] persist+flush 성공 — thread={}, propertyId={}",
                Thread.currentThread().getName(), propertyId);

    } catch (PersistenceException e) {
        log.info("[B-PK] PK 충돌 또는 INSERT 실패 — thread={}, propertyId={}",
                Thread.currentThread().getName(), propertyId);

        throw new DuplicatePropertyException(
                "동시 등록 충돌: 동일 매물이 다른 사용자에 의해 먼저 등록되었습니다. propertyId=" + propertyId, e);
    }

    // ── F008: afterCommit 콜백 — RDB 커밋 확정 후 Redis 동기화 ──
    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeRedisSyncWithCompensation(entity);
                }
            }
    );

    return PropertyCreateResponseDto.builder()
            .propertyId(propertyId).leaseType(LEASE_CHARTER_CODE)
            .dataSource(DataSource.USER.name()).status(PropertyStatus.ACTIVE.name())
            .registeredUserId(userId).registeredAt(now)
            .build();
}
```

F008 검증 당시(2026-04-29) 코드는 4번이 `charterRepository.save(entity)`였다. 그 뒤 F006 작업에서 `persist()` + `flush()`로 바꿨다. 콜백을 등록하는 위치와 F008 동작은 그때와 같다.

### 6.3. executeRedisSyncWithCompensation()

이 메서드는 afterCommit 콜백 안에서 호출되며, 발생한 예외를 네 가지로 나눠 처리한다.

| 예외 | 분류 | 처리 |
|---|---|---|
| `RedisConnectionFailureException` | 연결 실패, 일시적일 수 있음 | 200ms 대기 후 재시도 1회 |
| `QueryTimeoutException` | 타임아웃, 일시적일 수 있음 | 200ms 대기 후 재시도 1회 |
| `RedisSystemException` | OOM 등 인프라 장애, 재시도 의미 없음 | 즉시 실패 기록 |
| 그 외 `Exception` | 예상하지 못한 예외 | 즉시 실패 기록 |

설계 단계에서는 Lettuce 예외(`RedisCommandTimeoutException`, `RedisCommandExecutionException`)를 catch 대상으로 적었다. 구현에서는 Spring `DataAccessException` 계열로 바꿨다. `RedisTemplate`이 Lettuce 예외를 이 계열로 번역해서 던지므로, Lettuce 예외를 직접 catch하면 잡히지 않기 때문이다.

```java
private void executeRedisSyncWithCompensation(PropertyCharterEntity entity) {

    // 1차 시도
    try {
        syncRedisAfterCreate(entity);
        return;
    } catch (RedisConnectionFailureException e) {
        log.warn("[REDIS_SYNC_RETRY] 연결 실패, 재시도 진입: propertyId={}", entity.getPropertyId());
    } catch (QueryTimeoutException e) {
        log.warn("[REDIS_SYNC_RETRY] 타임아웃, 재시도 진입: propertyId={}, cause={}",
                entity.getPropertyId(), e.getClass().getSimpleName());
    } catch (RedisSystemException e) {
        log.error("[REDIS_SYNC_INFRA] 인프라 장애(OOM 등), 재시도 생략: propertyId={}, msg={}",
                entity.getPropertyId(), e.getMessage());
        recordSyncFailure(entity.getPropertyId(), e.getMessage());
        return;
    } catch (Exception e) {
        log.error("[REDIS_SYNC_UNEXPECTED] 예상 외 예외: propertyId={}, msg={}",
                entity.getPropertyId(), e.getMessage());
        recordSyncFailure(entity.getPropertyId(), e.getMessage());
        return;
    }

    // 2차 시도 (재시도 1회)
    try {
        Thread.sleep(200);
        syncRedisAfterCreate(entity);
        log.info("[REDIS_SYNC_RETRY_OK] 재시도 성공: propertyId={}", entity.getPropertyId());
        return;
    } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
    } catch (Exception retryEx) {
        log.warn("[REDIS_SYNC_RETRY_FAIL] 재시도 실패: propertyId={}", entity.getPropertyId());
    }

    // 재시도도 실패 → 실패 테이블 기록, 스케줄러 위임
    recordSyncFailure(entity.getPropertyId(), "즉시 재시도 1회 실패 후 스케줄러 위임");
}
```

모든 예외가 이 메서드 안에서 처리되므로 afterCommit 콜백 밖으로 예외가 전파되지 않는다. Redis 실패에도 사용자가 201을 받는 이유가 이것이다.

### 6.4. recordSyncFailure()

`TransactionTemplate` + `PROPAGATION_REQUIRES_NEW`로 독립 트랜잭션을 열어 INSERT + commit한다. 독립 트랜잭션이 필요한 이유는 7장에서 다룬다. 이 저장 자체가 실패하면 로그만 남기고 끝낸다.

```java
private void recordSyncFailure(String propertyId, String failReason) {

    try {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        txTemplate.execute(status -> {
            PropertySyncFailure failure = PropertySyncFailure.builder()
                    .propertyId(propertyId)
                    .leaseType("CHARTER")
                    .operationType("CREATE")
                    .failStep("FULL")
                    .failReason(failReason != null && failReason.length() > 500
                            ? failReason.substring(0, 500) : failReason)
                    .failTime(LocalDateTime.now())
                    .retryCount(0)
                    .maxRetries(5)
                    .resolved("N")
                    .build();

            syncFailureRepository.save(failure);
            return null;
        });

        log.warn("[SYNC_FAILURE_RECORDED] propertyId={}", propertyId);

    } catch (Exception e) {
        log.error("[SYNC_FAILURE_RECORD_FAILED] propertyId={}, error={}",
                propertyId, e.getMessage());
    }
}
```

### 6.5. retrySyncForCreate()

스케줄러가 재시도할 때 호출하는 public 메서드다. `syncRedisAfterCreate()`가 private이라 스케줄러가 직접 호출할 수 없으므로 만들었다.

하는 일은 두 가지다. `propertyId`로 Oracle에서 매물을 다시 읽고, 그 매물로 `syncRedisAfterCreate()`를 실행한다. Oracle에 쓰는 것은 없고 Redis에만 쓴다.

예외는 여기서 잡지 않는다. Redis 동기화가 실패하면 예외가 스케줄러까지 전파되고, 스케줄러가 그것을 보고 재시도 횟수를 올린다. 매물이 Oracle에 없으면 `PropertyNotFoundException`을 던지고, 스케줄러는 이 예외를 따로 받아 Redis 남은 데이터 정리로 처리한다(6.7).

```java
public void retrySyncForCreate(String propertyId) {
    PropertyCharterEntity entity = charterRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException(
                    "재시도 대상 매물이 존재하지 않습니다. propertyId=" + propertyId));

    syncRedisAfterCreate(entity);   // Redis에만 저장하는 코드, RDBMS 는 관계 없음.

    // 임시
    TransactionInterceptor interceptor = new TransactionInterceptor();
}
```

### 6.6. syncRedisAfterCreate() (변경 없음)

```java
private void syncRedisAfterCreate(PropertyCharterEntity entity) {

    String propertyId = entity.getPropertyId();
    String districtName = entity.getDistrictName();

    String hashKey = "property:charter:" + propertyId;
    Map<String, Object> hashFields = propertyHashBuilder.buildCharterHash(entity);
    redisHandler.redisTemplate.opsForHash().putAll(hashKey, hashFields);

    redisHandler.redisTemplate.opsForZSet().add(
            "idx:charterPrice:" + districtName, propertyId,
            entity.getDeposit().doubleValue());

    redisHandler.redisTemplate.opsForZSet().add(
            "idx:area:" + districtName + ":전세", propertyId,
            entity.getAreaInPyeong().doubleValue());

    String boundsKey = "bounds:" + districtName + ":전세";
    boundsUpdater.tryExtend(boundsKey, "minPrice", "maxPrice",
            entity.getDeposit().doubleValue(), PRICE_ZERO_DELTA);
    boundsUpdater.tryExtend(boundsKey, "minArea", "maxArea",
            entity.getAreaInPyeong().doubleValue(), AREA_ZERO_DELTA);

    log.info("전세 매물 Redis 동기화(등록) 완료: propertyId={}", propertyId);
}
```

### 6.7. PropertySyncRetryScheduler

`@Scheduled(fixedDelay = 60000)`으로 실행하므로 이전 실행이 끝난 뒤 60초 후에 다음 실행이 시작된다. 매 실행에서 미해결 레코드를 실패 시각 오름차순으로 처리한다.

```text
retryFailedSync():
    미해결 목록 = failureRepository.findByResolvedOrderByFailTimeAsc("N")

    for each 실패:

        분기 1: retryCount >= maxRetries (상한 초과)
            try:
                charterRepository.deleteById(propertyId)      // Oracle DELETE
                cleanupRedisResidual(propertyId)              // Redis 잔류 데이터 정리
                resolved = "Y", resolvedMethod = "ROLLBACK", resolvedTime = now
                failureRepository.save(실패)
                log.error("[SYNC_ROLLBACK] ...")
            catch (Exception):
                log.error("[SYNC_ROLLBACK_FAILED] ...")       // 다음 주기에 다시 시도
            continue

        분기 2: retryCount < maxRetries (재시도)
            try:
                charterWriteService.retrySyncForCreate(propertyId)
                resolved = "Y", resolvedMethod = "RETRY", resolvedTime = now
                failureRepository.save(실패)
                log.info("[SYNC_RETRY_OK] ...")
            catch (PropertyNotFoundException):                // 매물이 다른 경로(F003 등)로 이미 삭제됨
                cleanupRedisResidual(propertyId)
                resolved = "Y", resolvedMethod = "ROLLBACK", resolvedTime = now
                failureRepository.save(실패)
                log.warn("[SYNC_ENTITY_GONE] ...")
            catch (Exception):
                retryCount += 1
                failureRepository.save(실패)
                log.warn("[SYNC_RETRY_FAIL] ...")
```

`cleanupRedisResidual()`:

```text
cleanupRedisResidual(propertyId):
    try:
        hashKey = "property:charter:" + propertyId
        districtName = HGET hashKey districtName        // 부분 실패로 Hash가 남아 있을 수 있음
        if districtName != null:
            ZREM idx:charterPrice:{districtName} propertyId
            ZREM idx:area:{districtName}:전세 propertyId  // 없어도 0 반환, 에러 없음
        DEL hashKey                                      // 없어도 false 반환, 에러 없음
    catch (Exception):
        log.error("[REDIS_CLEANUP_FAILED] ...")          // 로그만 남기고 ROLLBACK 흐름은 계속
```

bounds는 정리하지 않는다. bounds는 여러 매물의 최솟값/최댓값이라서, 매물 하나를 제거했다고 축소하면 다른 매물의 경계값을 깨뜨리기 때문이다. 그래서 `BoundsUpdater`는 경계 확장만 처리하고, 축소와 재산출은 F009에서 다룬다.

### 6.8. 최종 흐름

```text
createProperty() 시작 → @Transactional 트랜잭션 시작
  → 입력값·중복 검증
  → persist + flush로 Oracle INSERT SQL 전송
  → afterCommit 콜백 등록 (이 시점에는 실행되지 않음)
  → 메서드 정상 종료 → @Transactional이 commit
  → afterCommit 실행 (요청 스레드, 동기)
      → Redis 동기화 1차 시도
      → 연결 실패·타임아웃이면 200ms 후 1회 재시도
      → 최종 실패 시 recordSyncFailure() → REQUIRES_NEW로 독립 commit
  → 201 응답 반환
  → 스케줄러 (1분 주기, PROPERTY_SYNC_FAILURES에서 RESOLVED='N'인 레코드 조회)
      → 재동기화 성공 → 그 레코드를 RESOLVED='Y', RESOLVED_METHOD='RETRY', RESOLVED_TIME=현재 시각으로 UPDATE
      → 재동기화 실패 → 그 레코드의 RETRY_COUNT를 1 올려 UPDATE
      → RETRY_COUNT >= MAX_RETRIES → PROPERTIES_CHARTER에서 매물 DELETE + Redis 정리
                                   → 그 레코드를 RESOLVED='Y', RESOLVED_METHOD='ROLLBACK', RESOLVED_TIME=현재 시각으로 UPDATE
```

---

## 7. 트러블슈팅: afterCommit 내부 실패 기록이 DB에 남지 않는 문제

### 7.1. 현상

Redis를 중지한 상태에서 매물을 등록하는 검증(8.3)을 처음 돌렸을 때, Redis 동기화 실패 기록이 `PROPERTY_SYNC_FAILURES` 테이블에 남지 않았다. 코드는 실패 기록을 저장했다고 로그를 출력했는데, Oracle을 조회하면 0건이었다.

그때 `recordSyncFailure()`는 다음처럼 `syncFailureRepository.save(failure)`만 호출하는 코드였다.

```java
private void recordSyncFailure(String propertyId, String failReason) {
    PropertySyncFailure failure = PropertySyncFailure.builder()
            // ... 필드 설정 ...
            .build();

    syncFailureRepository.save(failure);

    log.warn("[SYNC_FAILURE_RECORDED] propertyId={}", propertyId);
}
```

```text
save() 호출 성공, 예외 없음
[SYNC_FAILURE_RECORDED] 로그 출력
Oracle: SELECT * FROM PROPERTY_SYNC_FAILURES WHERE PROPERTY_ID = 'aa2114b966c7d4aec522192ac905dc59';  → 0건
```

실패 기록이 남지 않으면 스케줄러가 할 일이 없어진다. 스케줄러는 `PROPERTY_SYNC_FAILURES`에서 미해결 레코드를 읽어 재시도 대상을 찾는데, 레코드가 없으니 재시도도 삭제도 일어나지 않는다. 그러면 Oracle에는 매물이 있는데 Redis에는 없는 상태가 그대로 남고, 시스템은 그런 매물이 있다는 사실 자체를 알지 못한다. 이 상태를 없애는 것이 F008의 목적이었다.

### 7.2. 원인

원인은 세 가지가 겹친 것이다. `save()`는 commit이 아니다(7.2.1). afterCommit 안에서 호출한 `save()`에는 뒤따르는 commit이 없다(7.2.2). `save()`는 새 트랜잭션을 열지 않고 commit이 끝난 그 트랜잭션에 참여한다(7.2.3).

#### 7.2.1. `save()`는 commit이 아니다

`repository.save(entity)`가 호출된 뒤 데이터가 DB에 확정되기까지는 세 단계를 거친다.

1. `save()`는 `EntityManager.persist()` 또는 `merge()`를 호출하여 엔티티를 영속성 컨텍스트에 넣는다. 영속성 컨텍스트는 JPA가 메모리에서 엔티티를 관리하는 영역이다. 이 단계에서는 INSERT SQL을 만들지 않는다.
2. flush 시점에 JPA가 영속성 컨텍스트의 변경을 INSERT SQL로 만들어 DB 커넥션으로 보낸다. flush는 트랜잭션 commit 직전에 자동으로 실행되고, `flush()`를 직접 호출하면 즉시 실행된다.
3. Oracle은 INSERT SQL을 받으면 행을 삽입하되 아직 확정하지 않는다. 이 행은 해당 트랜잭션의 세션에서만 조회되고 다른 세션에서는 조회되지 않는다. 트랜잭션이 commit되면 행이 확정되어 모든 세션에서 조회되고, rollback되면 삽입이 취소된다.

`save()`가 하는 일은 1단계뿐이다. 그런데도 일반적인 `@Transactional` 메서드에서 `save()`만 호출하면 데이터가 남는다. 메서드가 끝날 때 Spring이 트랜잭션을 commit하면서 2단계(flush)와 3단계(확정)를 실행하기 때문이다. 저장을 확정한 것은 `save()`가 아니라 메서드 종료 시의 commit이다.

#### 7.2.2. afterCommit 안의 `save()`는 뒤따르는 commit이 없다

콜백 안에서 호출한 `save()`는 commit이 이미 끝난 `createProperty()`의 트랜잭션 안에서 실행된다. 그래서 이 `save()` 뒤에는 commit이 없다.

`createProperty()`가 반환된 뒤 `@Transactional` AOP가 처리하는 순서는 다음과 같다.

```text
createProperty() (@Transactional)
  → persist + flush
  → afterCommit 콜백 등록
  → 메서드 정상 종료

Spring 트랜잭션 AOP
  → 메인 트랜잭션 commit                    ← 여기서 commit이 끝남

afterCommit() 실행                          ← 이 안에서 save()를 호출
  → syncRedisAfterCreate() 실패
  → recordSyncFailure()
    → syncFailureRepository.save(failure)

트랜잭션 리소스 정리
```

`save()`는 엔티티를 영속성 컨텍스트에 넣을 뿐이고, INSERT SQL 전송(flush)과 commit은 메서드 종료 시 Spring이 실행한다(7.2.1). 흐름도대로 `afterCommit()`은 메인 트랜잭션의 commit이 끝난 뒤에 실행된다. 한 트랜잭션의 commit은 한 번이므로, `afterCommit()` 안에서 `save()`를 호출해도 그 엔티티는 flush되지도 commit되지도 않는다.

#### 7.2.3. `save()`는 새 트랜잭션을 열지 않고 commit이 끝난 트랜잭션에 참여한다

메인 commit이 끝났으면 `save()`가 새 트랜잭션을 열어 commit할 것이라고 생각했지만, 그렇게 동작하지 않았다.

7.2.2에서 본 대로 `afterCommit()`은 commit 직후, 트랜잭션 리소스를 정리하는 `cleanupAfterCompletion()` 전에 실행된다. 이 구간에서는 현재 스레드에 기존 트랜잭션의 리소스(EntityManager, Connection)가 아직 바인딩되어 있다.

`REQUIRED`(`@Transactional` 전파 속성의 기본값)는 현재 스레드에 트랜잭션 리소스가 있으면 새 트랜잭션을 열지 않고 거기에 참여한다. 그래서 `save()`는 이미 commit이 끝난 트랜잭션에 참여한 것으로 처리되었고, 참여자는 독립 commit을 하지 않는다.

결과적으로 `save()`는 성공하고 로그도 찍혔지만, 그 변경을 확정할 commit이 어디에도 없었다.

전파 속성(propagation)은 `@Transactional` 메서드가 호출된 시점에 이미 진행 중인 트랜잭션이 있을 때 어떻게 동작할지를 정하는 설정이다. 진행 중인 트랜잭션에 참여할지, 새 트랜잭션을 열지, 트랜잭션 없이 실행할지가 값마다 다르다. Spring이 제공하는 값은 아래 표와 같다. 이 문서에서 쓰는 값은 `REQUIRED`(기본값)와 `REQUIRES_NEW`(7.3)다.

| 값 | 진행 중인 트랜잭션이 있을 때 | 진행 중인 트랜잭션이 없을 때 |
|---|---|---|
| `REQUIRED` | 그 트랜잭션에 참여한다. 독립 commit·rollback을 하지 않는다 | 새 트랜잭션을 시작한다 |
| `REQUIRES_NEW` | 그 트랜잭션을 일시 중단하고 새 트랜잭션을 시작한다. 새 트랜잭션이 끝나면 중단했던 트랜잭션을 재개한다 | 새 트랜잭션을 시작한다 |
| `SUPPORTS` | 그 트랜잭션에 참여한다 | 트랜잭션 없이 실행한다 |
| `NOT_SUPPORTED` | 그 트랜잭션을 일시 중단하고 트랜잭션 없이 실행한다 | 트랜잭션 없이 실행한다 |
| `MANDATORY` | 그 트랜잭션에 참여한다 | `IllegalTransactionStateException`을 던진다 |
| `NEVER` | `IllegalTransactionStateException`을 던진다 | 트랜잭션 없이 실행한다 |
| `NESTED` | 그 트랜잭션 안에 savepoint를 만들고 중첩 트랜잭션으로 실행한다. 중첩 부분만 savepoint까지 rollback할 수 있다 | 새 트랜잭션을 시작한다 |

Spring `TransactionSynchronization.afterCommit()` Javadoc도 같은 내용을 명시한다. "transactional resources might still be active and accessible ... data access code triggered at this point will still participate in the original transaction (with no commit following anymore). Use PROPAGATION_REQUIRES_NEW for any transactional operation that is called from here."

- 출처: Spring Framework API 문서(Javadoc), `org.springframework.transaction.support.TransactionSynchronization#afterCommit()`
- 링크: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCommit()

### 7.3. 해결

`recordSyncFailure()`의 실패 기록 INSERT를 전파 속성이 `PROPAGATION_REQUIRES_NEW`인 `TransactionTemplate` 안에서 실행하도록 바꿨다. 이 INSERT는 `createProperty()`의 트랜잭션과 분리된 새 트랜잭션에서 실행되고, 그 트랜잭션이 자체 commit한다. 바뀐 코드 전체는 6.4이다.

#### 7.3.1. 주요 변경 사항

`recordSyncFailure()`가 실패 기록 INSERT를 `REQUIRES_NEW` 트랜잭션에서 실행하도록 코드 네 곳을 바꿨다.

1. `CharterPropertyWriteService`에 `private final PlatformTransactionManager transactionManager` 필드를 추가했다. `TransactionTemplate` 생성자에 이 필드를 인자로 전달한다.
2. `recordSyncFailure()`에서 `new TransactionTemplate(transactionManager)`로 `TransactionTemplate` 객체를 만든다.
3. 그 객체의 `setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)`를 호출하여 전파 속성을 `REQUIRES_NEW`로 지정한다.
4. `syncFailureRepository.save(failure)` 호출을 `txTemplate.execute()` 안으로 옮겼다. `execute()` 실행이 끝나면 `TransactionTemplate`이 새 트랜잭션을 commit한다.

#### 7.3.2. 수정 후 처리 흐름

```text
afterCommit()
  → recordSyncFailure()
  → TransactionTemplate(REQUIRES_NEW).execute()
      → 기존 리소스가 남아 있어도 독립 트랜잭션 시작
      → syncFailureRepository.save(failure)
      → 새 트랜잭션 commit
  → 실패 기록 DB 확정
```

`REQUIRES_NEW`는 현재 스레드에 `createProperty()`의 트랜잭션 리소스가 남아 있어도 그것과 별개로 새 트랜잭션을 시작한다(7.2.3 표). 그래서 `execute()` 안의 `save()`는 commit이 끝난 트랜잭션이 아니라 이 새 트랜잭션에 참여한다.

`execute()` 실행이 끝나면 `TransactionTemplate`이 새 트랜잭션을 commit한다. 이 commit이 실행될 때 `save()`의 INSERT가 flush되어 함께 commit된다.

수정 전(7.2.2)에는 `save()`를 호출한 다음 commit이 실행되지 않았다. 수정 후에는 `TransactionTemplate`이 commit을 실행한다.

#### 7.3.3. 수정 후 실패 기록이 `PROPERTY_SYNC_FAILURES`에 남는다

수정 후 Redis를 중지한 상태에서 매물 등록을 다시 요청했다. 콘솔에 `[SYNC_FAILURE_RECORDED]` 로그가 출력되었고, `PROPERTY_SYNC_FAILURES` 테이블을 조회하니 그 매물의 레코드 1건이 있었다.

---

## 8. 검증 결과

구현한 코드가 F008이 지켜야 할 조건을 지키는지 시나리오 6개로 확인했고, 6개 모두 통과했다. 지켜야 할 조건은 commit 전에는 Redis에 쓰지 않고, commit 이후의 Redis 실패는 기록하고, 일시적 장애는 재시도로 복구하고, 장기 장애는 Oracle 매물을 삭제한다는 것이다.

시나리오마다 Redis를 가동 또는 중지한 상태에서 등록 요청을 보내거나 스케줄러 실행을 기다렸다. 그 뒤 Oracle, Redis, 실패 테이블의 조회 결과와 콘솔 로그를 대조했다. 시나리오 1은 `F008검증아파트`/`100-1`로, 시나리오 2~6은 `F008검증아파트2`/`200-2`로 요청하여 PK 충돌을 피했다.

테스트 환경은 부록 10.1, 요청 본문은 부록 10.2이다.

### 8.1. 시나리오 6개의 결과

시나리오 6개의 결과를 시나리오별 표로 나눠 적는다. 각 표는 사용자 응답, Oracle과 Redis의 저장 상태, 실패 테이블의 상태, 그 시나리오에서 확인한 동작을 담는다.

시나리오 1은 Oracle과 Redis를 모두 가동한 상태에서 매물을 등록한다.

| 항목 | 결과 |
|---|---|
| 응답 | 201 |
| Oracle | 존재 |
| Redis | 존재 |
| 실패 테이블 | 0건 |
| 확인한 동작 | afterCommit이 정상 등록에 영향을 주지 않는다 |
| 결과 | 통과 |

시나리오 2는 Redis를 중지한 상태에서 매물을 등록한다.

| 항목 | 결과 |
|---|---|
| 응답 | 201 |
| Oracle | 존재 |
| Redis | 없음 |
| 실패 테이블 | 1건 (RESOLVED=N) |
| 확인한 동작 | commit 이후 Redis 실패가 테이블에 기록된다 |
| 결과 | 통과 |

시나리오 3은 시나리오 2 이후 Redis를 중지한 채로 두고, 스케줄러가 재시도하기를 기다린다.

| 항목 | 결과 |
|---|---|
| 응답 | 해당 없음 |
| Oracle | 해당 없음 |
| Redis | 해당 없음 |
| 실패 테이블 | RETRY_COUNT 증가 |
| 확인한 동작 | 스케줄러가 1분마다 재시도한다 |
| 결과 | 통과 |

시나리오 4는 Redis를 재시작하고, 스케줄러가 재동기화하기를 기다린다.

| 항목 | 결과 |
|---|---|
| 응답 | 해당 없음 |
| Oracle | 해당 없음 |
| Redis | 복구 |
| 실패 테이블 | RESOLVED=Y, RESOLVED_METHOD=RETRY |
| 확인한 동작 | 스케줄러가 재동기화에 성공하고 레코드를 해결로 갱신한다 |
| 결과 | 통과 |

시나리오 5는 Redis를 중지한 상태에서 매물을 등록한 뒤 MAX_RETRIES를 2로 낮추고, 재시도 상한을 넘을 때까지 기다린다.

| 항목 | 결과 |
|---|---|
| 응답 | 201 |
| Oracle | DELETE |
| Redis | 정리 시도 (Redis 중지로 실패) |
| 실패 테이블 | RESOLVED=Y, RESOLVED_METHOD=ROLLBACK |
| 확인한 동작 | 상한을 넘으면 Oracle 매물을 삭제한다 |
| 결과 | 통과 |

시나리오 6은 Redis를 가동한 상태에서 INSERT 직후에 예외를 넣어 Oracle commit을 실패시킨다.

| 항목 | 결과 |
|---|---|
| 응답 | 500 |
| Oracle | 없음 |
| Redis | 없음 |
| 실패 테이블 | 0건 |
| 확인한 동작 | rollback 시 afterCommit이 실행되지 않는다 |
| 결과 | 통과 |

### 8.2. 시나리오 1: Redis 가동 상태에서 등록하면 Oracle과 Redis에 모두 저장된다

Oracle과 Redis를 모두 가동한 상태에서 `jibun` = `100-1`로 요청했다. Oracle과 Redis 양쪽에 저장되었고, 실패 테이블에는 기록이 없었으며, 에러 로그도 없었다.

응답은 `201 Created`였다. 응답 본문은 다음과 같다.

```json
{
    "property_id": "2f75f2e5257e56413325a7c2ac85a9f5",
    "lease_type": "CHARTER",
    "data_source": "USER",
    "status": "ACTIVE",
    "registered_user_id": "yhjj815",
    "registered_at": "2026-04-29T17:07:37"
}
```

Oracle, Redis, 실패 테이블을 조회한 결과는 아래 표와 같다.

| 확인 대상 | 명령 | 결과 |
|---|---|---|
| Oracle | `SELECT PROPERTY_ID, APT_NM, STATUS, DATA_SOURCE FROM PROPERTIES_CHARTER WHERE APT_NM = 'F008검증아파트' AND JIBUN = '100-1';` | 1건. ACTIVE, USER |
| Redis Hash | `EXISTS property:charter:2f75f2e5257e56413325a7c2ac85a9f5` | 1 |
| Redis ZSet | `ZSCORE idx:charterPrice:강남구 "\"2f75f2e5257e56413325a7c2ac85a9f5\""` | "45000" |
| 실패 테이블 | `SELECT COUNT(*) FROM PROPERTY_SYNC_FAILURES WHERE PROPERTY_ID = '2f75…';` | 0건 |

콘솔 로그는 다음과 같다.

```text
17:07:37.116 INFO  CharterPropertyWriteService - 전세 매물 Redis 동기화(등록) 완료: propertyId=2f75f2e5257e56413325a7c2ac85a9f5
17:07:37.164 INFO  PropertyWriteController - 전세 매물 등록 완료: propertyId=2f75f2e5257e56413325a7c2ac85a9f5
```

### 8.3. 시나리오 2: Redis 중지 상태에서 등록하면 Oracle에만 저장되고 실패 테이블에 기록된다

`redis-cli SHUTDOWN NOSAVE`로 Redis를 중지한 뒤 `jibun` = `200-2`로 요청했다. 사용자는 201을 받았고, Oracle에는 매물이 저장되었으며, 실패 테이블에 레코드 1건이 기록되었다.

응답은 `201 Created`였다. 응답 본문은 다음과 같다.

```json
{
    "property_id": "aa2114b966c7d4aec522192ac905dc59",
    "lease_type": "CHARTER",
    "data_source": "USER",
    "status": "ACTIVE",
    "registered_user_id": "yhjj815",
    "registered_at": "2026-04-29T17:37:07"
}
```

Oracle과 실패 테이블을 조회한 결과는 아래 표와 같다.

| 확인 대상 | 명령 | 결과 |
|---|---|---|
| Oracle | `SELECT PROPERTY_ID, STATUS FROM PROPERTIES_CHARTER WHERE APT_NM = 'F008검증아파트2' AND JIBUN = '200-2';` | 1건. ACTIVE |
| 실패 테이블 | `SELECT FAILURE_ID, PROPERTY_ID, RETRY_COUNT, MAX_RETRIES, RESOLVED, FAIL_REASON FROM PROPERTY_SYNC_FAILURES WHERE RESOLVED = 'N';` | FAILURE_ID=3, RETRY_COUNT=0, MAX_RETRIES=5, RESOLVED=N, FAIL_REASON="즉시 재시도 1회 실패 후 스케줄러 위임" |

콘솔 로그는 다음과 같다.

```text
17:37:07.249 WARN  CharterPropertyWriteService - [REDIS_SYNC_RETRY] 타임아웃, 재시도 진입: propertyId=aa2114b966c7d4aec522192ac905dc59, cause=QueryTimeoutException
17:38:07.454 WARN  CharterPropertyWriteService - [REDIS_SYNC_RETRY_FAIL] 재시도 실패: propertyId=aa2114b966c7d4aec522192ac905dc59
17:38:07.456 WARN  CharterPropertyWriteService - [SYNC_FAILURE_RECORDED] propertyId=aa2114b966c7d4aec522192ac905dc59
17:38:07.456 INFO  PropertyWriteController - 전세 매물 등록 완료: propertyId=aa2114b966c7d4aec522192ac905dc59
```

로그 순서는 6.3의 예외 처리 순서와 같다. `QueryTimeoutException`을 일시적 장애로 분류하여 200ms 뒤 재시도했고, 재시도가 실패하자 실패 테이블에 기록한 뒤 201로 응답했다.

이 시나리오를 처음 실행했을 때는 실패 기록이 테이블에 남지 않았다(7장). 위 결과는 7장의 수정 뒤에 다시 실행한 것이다.

### 8.4. 시나리오 3: Redis 중지가 지속되면 스케줄러가 재시도하고 RETRY_COUNT가 증가한다

시나리오 2 직후 Redis를 중지한 채 1분 이상 기다렸다. 스케줄러가 두 번 재시도했고 RETRY_COUNT가 0에서 2로 증가했다.

실패 테이블을 조회한 결과는 아래 표와 같다.

| 확인 대상 | 결과 |
|---|---|
| `SELECT FAILURE_ID, PROPERTY_ID, RETRY_COUNT, RESOLVED FROM PROPERTY_SYNC_FAILURES WHERE PROPERTY_ID = 'aa2114…';` | FAILURE_ID=3, RETRY_COUNT=2, RESOLVED=N |

콘솔 로그는 다음과 같다.

```text
[scheduling-1] WARN  PropertySyncRetryScheduler - [SYNC_RETRY_FAIL] 재시도 실패 (1회): ...
[scheduling-1] WARN  PropertySyncRetryScheduler - [SYNC_RETRY_FAIL] 재시도 실패 (2회): ...
```

### 8.5. 시나리오 4: Redis가 복구되면 스케줄러가 재동기화하고 레코드를 RESOLVED=Y로 갱신한다

`redis-server`로 Redis를 재시작하고 1분 기다렸다. 스케줄러 4회차에서 `retrySyncForCreate()`가 성공했고, 실패 레코드가 RESOLVED=Y, RESOLVED_METHOD=RETRY로 갱신되었다. 사용자는 최초 요청에서 201을 받았고, Redis 장애가 해소되자 추천 검색 인덱스가 회복되었다.

실패 테이블을 조회한 결과는 아래 표와 같다.

| 확인 대상 | 결과 |
|---|---|
| `SELECT FAILURE_ID, RETRY_COUNT, RESOLVED, RESOLVED_METHOD, RESOLVED_TIME FROM PROPERTY_SYNC_FAILURES WHERE PROPERTY_ID = 'aa2114…';` | FAILURE_ID=3, RETRY_COUNT=3, RESOLVED=Y, RESOLVED_METHOD=RETRY, RESOLVED_TIME=2026-04-29 17:45:04 |
| `SELECT COUNT(*) FROM PROPERTY_SYNC_FAILURES WHERE RESOLVED = 'N';` | 0건 |

### 8.6. 시나리오 5: 재시도 상한을 넘으면 Oracle 매물을 삭제한다 (ROLLBACK)

재시도 상한을 넘긴 뒤 스케줄러가 Oracle 매물을 삭제하고 실패 레코드를 RESOLVED=Y, RESOLVED_METHOD=ROLLBACK으로 갱신했다.

사전 작업으로 시나리오 2~4에서 만든 데이터를 지우고 Redis를 다시 중지했다.

```sql
DELETE FROM PROPERTIES_CHARTER WHERE PROPERTY_ID = 'aa2114b966c7d4aec522192ac905dc59';
DELETE FROM PROPERTY_SYNC_FAILURES;
COMMIT;
```

```bash
redis-cli SHUTDOWN NOSAVE
```

`jibun` = `200-2`로 다시 요청했고 응답은 `201 Created`였다. 테스트 시간을 줄이기 위해 MAX_RETRIES를 5에서 2로 바꿨다.

```sql
UPDATE PROPERTY_SYNC_FAILURES SET MAX_RETRIES = 2 WHERE FAILURE_ID = 4 AND RESOLVED = 'N';
COMMIT;
```

기대한 동작은 다음과 같다. 스케줄러가 두 번 실패하여 RETRY_COUNT가 0→1→2로 증가하고, 세 번째 실행에서 `retryCount(2) >= maxRetries(2)`가 되어 상한 초과 분기(Oracle 삭제)가 실행된다.

약 3분 뒤에 확인했다. 실패 테이블과 매물 테이블을 조회한 결과는 아래 표와 같다.

| 확인 대상 | 결과 |
|---|---|
| `SELECT FAILURE_ID, RETRY_COUNT, RESOLVED, RESOLVED_METHOD FROM PROPERTY_SYNC_FAILURES WHERE FAILURE_ID = 4;` | FAILURE_ID=4, RETRY_COUNT=2, RESOLVED=Y, RESOLVED_METHOD=ROLLBACK |
| `SELECT COUNT(*) FROM PROPERTIES_CHARTER WHERE PROPERTY_ID = 'aa2114…';` | 0건 (DELETE 확인) |

콘솔 로그는 다음과 같다.

```text
20:03:36.065 ERROR PropertySyncRetryScheduler - [REDIS_CLEANUP_FAILED] Redis 잔존 정리 실패: propertyId=aa2114b966c7d4aec522192ac905dc59, error=Redis command timed out
20:03:36.065 ERROR PropertySyncRetryScheduler - [SYNC_ROLLBACK] 재시도 상한 초과, RDB+Redis 원복 완료: propertyId=aa2114b966c7d4aec522192ac905dc59
```

### 8.7. 시나리오 6: RDB commit이 실패하면 afterCommit이 실행되지 않는다

Oracle이 rollback되면 afterCommit 콜백이 실행되지 않아 Redis와 실패 테이블에 아무것도 남지 않는다. 따라서 afterCommit 구조에서는 케이스 2-B(Oracle rollback + Redis Hash 남음)가 발생하지 않는다.

Redis를 가동한 상태에서, `createProperty()`의 INSERT 직후·afterCommit 등록 직전에 임시 예외를 넣었다. 넣은 코드는 다음과 같다.

```java
charterRepository.save(entity);

// ── F008 테스트: RDB 커밋 실패 시뮬레이션 ──
if (true) { throw new RuntimeException("[F008-TEST] RDB 커밋 실패 시뮬레이션"); }

TransactionSynchronizationManager.registerSynchronization(
```

데이터를 정리한 뒤 `jibun` = `200-2`로 다시 요청했다. 응답은 `500 Internal Server Error`였다. 콘솔 로그는 다음과 같다.

```text
20:23:27.721 INFO  PropertyWriteController - 전세 매물 등록 요청: userId=yhjj815, aptNm=F008검증아파트2, sggCd=11680
20:23:27.731 INFO  CharterPropertyWriteService - 매물 ID=aa2114b966c7d4aec522192ac905dc59
20:23:27.752 ERROR o.a.c.c.C.[.[.[/wherehouse] - RuntimeException: [F008-TEST] RDB 커밋 실패 시뮬레이션
```

afterCommit 안에서 출력되는 로그(6.3의 `[REDIS_SYNC_*]`와 `[SYNC_FAILURE_RECORDED]`, 6.6의 동기화 완료 로그)는 하나도 출력되지 않았다.

Oracle, 실패 테이블, Redis를 확인한 결과는 아래 표와 같다.

| 확인 대상 | 결과 |
|---|---|
| Oracle `WHERE APT_NM = 'F008검증아파트2' AND JIBUN = '200-2'` | 0건 (rollback) |
| 실패 테이블 `WHERE RESOLVED = 'N'` | 0건 |
| Redis | 신규 데이터 없음. afterCommit이 실행되지 않아 `syncRedisAfterCreate()`가 호출되지 않았다 |

실행 흐름은 다음과 같다.

```text
createProperty() 시작 → 트랜잭션 시작
  → INSERT SQL 전송
  → RuntimeException → afterCommit 등록 코드에 도달하지 못함
  → rollback
  → 등록된 콜백이 없으므로 실행할 것 없음
  → 500 응답
```

테스트 후 삽입한 예외 코드를 제거했다.

---

## 9. 결론

이 작업은 "Oracle에 commit되지 않은 매물은 Redis에 반영되면 안 된다"와 "Redis 동기화 실패를 무한히 방치하면 안 된다"는 두 조건을 기준으로, 전세 매물 등록에서 Redis 동기화의 실행 시점과 실패 처리 범위를 다시 정한 것이다. 기존 코드는 Redis 동기화를 `@Transactional` 경계 안에서 Oracle commit 전에 실행했다. Redis는 Oracle 트랜잭션의 rollback 대상이 아니므로 이 순서에서는 Redis 전체 실패 시 Oracle에만 매물이 남고 부분 실패 시 Oracle rollback 뒤에 Redis Hash가 남았다. 그래서 첫 번째로 Redis 동기화를 `afterCommit` 콜백으로 옮겨 commit 이후에만 실행하게 했고, 두 번째로 commit 이후의 Redis 실패를 처리하는 보상 구조를 붙였다.

commit 이후 Redis 실패의 대응으로 실패 응답 반환, 기록 후 자동 재시도, 상한 초과 시 Oracle 삭제를 비교했고, 뒤의 두 가지를 함께 적용했다. Redis 장애는 일시적일 수 있으므로, 첫 실패에서 응답을 실패로 돌리면 재시도했으면 성공했을 등록이 실패로 끝난다. 상한을 넘긴 뒤 Oracle 매물을 유지하면 불일치가 끝나지 않는다.

이 선택으로 전세 매물 등록은 다음을 보장한다.

1. Oracle commit 전에는 Redis에 쓰지 않는다.
2. Oracle이 rollback되면 Redis 동기화가 실행되지 않는다.
3. commit 이후의 Redis 실패는 `PROPERTY_SYNC_FAILURES`에 기록된다.
4. 일시적 장애는 즉시 재시도 1회와 스케줄러 재시도로 복구된다.
5. 장기 장애는 Oracle 매물 삭제로 끝난다.
6. `afterCommit` 안에서 실행하는 실패 기록 INSERT는 독립 commit된다.

6번은 구현 중에 만난 문제의 해결이다. `afterCommit` 안에서 호출한 `save()`는 commit이 이미 끝난 트랜잭션에 참여하므로 commit되지 않았고, 실패 기록 INSERT를 `TransactionTemplate` + `PROPAGATION_REQUIRES_NEW`로 새 트랜잭션에서 실행하도록 바꿔 해결했다. 이 기록이 없으면 스케줄러가 재시도할 매물을 찾지 못하므로, 6번이 없으면 3~5번이 동작하지 않는다.

검증에서는 RDB commit 실패 시나리오로 1·2번을, Redis 중지·복구·상한 초과 시나리오로 3~6번을 확인했다. 그 결과 Oracle에만 있거나 Redis에만 있거나 Redis에 일부만 있는 상태는 계속되지 않고, 최종 상태는 양쪽 모두 있음 또는 양쪽 모두 없음으로 정리된다.

---

## 10. 부록

### 10.1. 테스트 환경

| 항목 | 값 |
|---|---|
| 서버 포트 / 컨텍스트 경로 | 8185 / `/wherehouse` |
| Oracle | 127.0.0.1:1521:xe, SCOTT/tiger |
| Redis | 127.0.0.1:6379, 패스워드 없음 |
| Jackson | property-naming-strategy: SNAKE_CASE |
| HikariCP auto-commit | false |
| Redis value serializer | `Jackson2JsonRedisSerializer` (key는 `StringRedisSerializer`) |
| Spring Boot | `@EnableScheduling` 활성 |
| 확인 도구 | Postman, Oracle SQL Developer, redis-cli, Spring Boot 콘솔 로그 |

### 10.2. 요청

`POST http://localhost:8185/wherehouse/api/v1/properties/charter`, `Content-Type: application/json`

문제 재현 테스트(3장)에서 보낸 본문은 다음과 같다.

```json
{
    "apt_nm": "F008테스트아파트",
    "sgg_cd": "11680",
    "umd_nm": "역삼동",
    "jibun": "999-99",
    "floor": 5,
    "exclu_use_ar": 59.84,
    "deposit": 45000,
    "build_year": 2020,
    "deal_date": "2026-04-28"
}
```

검증(8장)에서는 `apt_nm`과 `jibun`을 시나리오 1은 `F008검증아파트`/`100-1`, 시나리오 2~6은 `F008검증아파트2`/`200-2`로 바꾼 것 외에는 같은 본문을 보냈다.

### 10.3. DDL

```sql
CREATE TABLE PROPERTY_SYNC_FAILURES (
    FAILURE_ID      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    PROPERTY_ID     VARCHAR2(32)  NOT NULL,
    LEASE_TYPE      VARCHAR2(10)  NOT NULL,
    OPERATION_TYPE  VARCHAR2(20)  NOT NULL,
    FAIL_STEP       VARCHAR2(50)  NOT NULL,
    FAIL_REASON     VARCHAR2(500),
    FAIL_TIME       TIMESTAMP DEFAULT SYSTIMESTAMP,
    RETRY_COUNT     NUMBER DEFAULT 0,
    MAX_RETRIES     NUMBER DEFAULT 5,
    RESOLVED        CHAR(1) DEFAULT 'N',
    RESOLVED_TIME   TIMESTAMP,
    RESOLVED_METHOD VARCHAR2(20)
);
```

### 10.4. Entity

```java
@Entity
@Table(name = "PROPERTY_SYNC_FAILURES")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PropertySyncFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAILURE_ID")
    private Long failureId;

    @Column(name = "PROPERTY_ID", length = 32, nullable = false)
    private String propertyId;

    @Column(name = "LEASE_TYPE", length = 10, nullable = false)
    private String leaseType;

    @Column(name = "OPERATION_TYPE", length = 20, nullable = false)
    private String operationType;

    @Column(name = "FAIL_STEP", length = 50, nullable = false)
    private String failStep;

    @Column(name = "FAIL_REASON", length = 500)
    private String failReason;

    @Column(name = "FAIL_TIME")
    private LocalDateTime failTime;

    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    @Column(name = "MAX_RETRIES")
    private Integer maxRetries;

    @Column(name = "RESOLVED", length = 1)
    private String resolved;

    @Column(name = "RESOLVED_TIME")
    private LocalDateTime resolvedTime;

    @Column(name = "RESOLVED_METHOD", length = 20)
    private String resolvedMethod;
}
```
