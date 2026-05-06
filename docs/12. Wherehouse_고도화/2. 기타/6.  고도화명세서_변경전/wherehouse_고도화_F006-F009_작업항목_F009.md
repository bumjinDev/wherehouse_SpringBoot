# Wherehouse 고도화 — F006~F009 구현 작업 항목

**작성일:** 2026-04-27  
**최종 수정일:** 2026-05-06  
**목적:** F001~F005 CRUD 완료 상태에서, 설계 의사결정 케이스 4건(F006~F009)을 구현하기 위한 작업 항목 정리  
**구현 순서:** F008 → F006 → F009 → F007 (현재 코드에 이미 존재하는 문제부터 해결)

---

## 0. 사전 정리 — 현재 코드의 즉시 수정 사항

F006~F009 착수 전에 현재 코드의 기본 결함을 먼저 해소한다.

### 0-1. @Transactional 주석 해제

**대상 파일:**
- `CharterPropertyWriteService.java` — `updateProperty()`, `changeStatus()`
- `MonthlyPropertyWriteService.java` — `updateProperty()`, `changeStatus()`

**작업:** `// @Transactional` 주석을 해제하여 `@Transactional` 활성화.  
단, F008 작업에서 트랜잭션 경계 재설계가 수반되므로, 해제 후 바로 F008로 진입한다.

### 0-2. 패키지명 오타 수정

`execption` → `exception` 패키지명 리네이밍.

**작업:**
1. `com.wherehouse.PropertyManagement.execption` 패키지를 `com.wherehouse.PropertyManagement.exception`으로 변경
2. 해당 패키지를 import하는 모든 파일의 import문 일괄 수정 (IDE Refactor → Rename Package 사용)
3. 컴파일 확인

### 0-3. 공통 코드 추출

Charter/Monthly 양쪽에 중복된 코드를 공통 클래스로 추출한다.

**작업:**
1. `PropertyManagement` 패키지 하위에 `common` (또는 적절한 이름) 패키지 생성
2. `SeoulDistrictCodes` 상수 클래스 생성 — `SEOUL_DISTRICT_CODES` Map + `validateDistrictCode()` + `resolveDistrictName()` 이동
3. `PropertyConversionUtil` 유틸 클래스 생성 — `toPyeong()`, `buildAddress()`, `longEqualsInteger()` 이동
4. Charter/Monthly 양쪽 서비스에서 공통 클래스 참조로 전환
5. 컴파일 + 기존 동작 확인

### 0-4. verifyOwnership 주석 처리 정합성 확보

`updateProperty()`에서 `verifyOwnership` 주석 처리가 요구사항 변경에 의한 것이면:
1. 해당 주석을 제거하고 코드 자체를 삭제 (또는 명확한 주석으로 대체: "F002 요구사항 v2.0: 인증된 모든 사용자 수정 허용")
2. 요구사항 명세서와 Javadoc의 권한 정책 기술이 일치하는지 확인

---

## 1. F008 — 검색 인덱스 동기화 (부분 실패 보상)

### 핵심 의사결정 질문
> "RDB 커밋과 Redis 동기화 사이에서, 커밋 전 Redis 반영 vs 커밋 후 Redis 반영 중 어느 쪽이 이 시스템에 적합한가? 부분 실패 시 어떻게 보상하는가?"

### 1-1. 현재 문제 분석 및 문서화

**현재 코드의 문제를 정확히 기술한다.**

현재 코드는 Write-Through 구조로, 서비스 메서드 내에서 RDB 저장과 Redis 동기화를 동일 호출 흐름에서 순차 실행한다.

```
createProperty() 진입
  → charterRepository.save(entity)     // Oracle INSERT
  → syncRedisAfterCreate(entity)       // Redis Hash + ZSet + bounds 반영
  → 응답 반환
```

이 구조에서 발생 가능한 실패 시나리오 2가지를 식별하고, 인위적 예외 주입 테스트로 실증한다.

---

**문제 시나리오 1 — Redis 전체 실패**

Redis 서버 다운, 연결 타임아웃 등으로 Redis 동기화가 전체 실패하는 경우.

```
save(entity) 성공 → syncRedisAfterCreate() 전체 실패
```

- 결과: RDB에 ACTIVE 매물이 존재하지만 Redis에는 Hash·인덱스 모두 부재
- 영향: 매물 목록 조회(F004)에서는 표시되나, 추천 검색(Redis 기반)에서 완전 누락

`@Transactional` 적용 여부에 따른 동작 차이:

| 조건 | Oracle | Redis | 정합성 | 후속 문제 |
|------|--------|-------|--------|----------|
| `@Transactional` 미적용 | 커밋 확정 (잔존) | 없음 | **불일치** | 재등록 시 PK 중복 → 자가 복구 불가 |
| `@Transactional` 적용 | 롤백 (없음) | 없음 | 일치 | Redis 장애가 등록 자체를 차단 (과도한 결합) |

> 테스트 완료 — `F008_시나리오1_Redis전체실패_테스트결과.md` 참조

---

**문제 시나리오 2 — Redis 부분 실패**

Redis 동기화 내부 작업(Hash, ZSet, bounds) 중 일부만 성공하는 경우.

```
save(entity) 성공 → Hash 저장 성공 → ZSet 추가 실패 → bounds 미실행
```

- 결과: Hash는 존재하지만 인덱스에는 없는 반쪽짜리 상태
- 영향: 매물 상세 조회(Hash 기반)는 가능하나 추천 검색(Sorted Set 교집합 기반)에서 누락
- 추가 발견: `@Transactional` 적용 시 Oracle은 롤백되지만 이미 확정된 Redis Hash는 롤백 대상이 아니므로, Oracle에 없는 매물의 유령 Hash가 Redis에 잔존하는 **시나리오 1보다 나쁜 불일치**가 발생한다.

> 테스트 완료 — `F008_시나리오2_Redis부분실패_테스트결과.md` 참조

---

#### 제외 시나리오 — 커밋 전 Redis 반영 (유령 데이터)

초기 분석 단계에서 다음 시나리오를 별도 문제 시나리오로 식별하였다.

```
syncRedisAfterCreate() 성공 → RDB 커밋 실패 → Redis에 유령 데이터 잔존, RDB에는 없음
```

본 테스트 과정에서 다음 근거로 독립 시나리오에서 **제외**하였다.

이 시나리오는 `@Transactional` 적용 시에만 발생한다. `@Transactional`이 없으면 `save()` 호출 시점에 즉시 커밋되므로 "커밋 전" 상태 자체가 존재하지 않는다. 즉, 이 시나리오는 운영 환경에서 자연 발생하는 장애가 아니라 **트랜잭션 경계를 어디에 설정하느냐에 따른 설계 판단의 결과물**이다.

시나리오 1(Redis 전체 실패)과 시나리오 2(Redis 부분 실패)의 테스트를 통해 RDB-Redis 간 정합성 문제의 본질을 실증한 뒤, 후속 설계 판단(1-2. 대안 비교 분석)에서 트랜잭션 경계 설계를 통해 구조적으로 해소한다.

### 1-2. 대안 비교 분석

최소 3개 대안을 비교한다.

**대안 A: TransactionSynchronizationManager.registerSynchronization(afterCommit)**
- 동작: RDB 커밋 확정 후에만 Redis 동기화 콜백 실행
- 장점: 커밋 전 Redis 반영(유령 데이터) 원천 차단. 기존 서비스 코드 구조 최소 변경.
- 단점: 커밋 후 Redis 실패 시 보상 로직 별도 필요. afterCommit 콜백 내 예외가 호출자에게 전파되지 않음.
- 구현 난이도: 중

**대안 B: @TransactionalEventListener(phase = AFTER_COMMIT) 이벤트 기반**
- 동작: 서비스에서 ApplicationEvent 발행 → 커밋 후 리스너가 Redis 동기화 수행
- 장점: 서비스 계층과 동기화 로직 완전 분리. 테스트 용이.
- 단점: 이벤트 발행/수신 인프라 추가. 동일 트랜잭션 내에서 이벤트 유실 가능성(Spring 기본 동작에서는 낮지만 고려 필요).
- 구현 난이도: 중~상

**대안 C: 서비스 메서드 분리 (트랜잭션 메서드 + 비트랜잭션 메서드)**
- 동작: `@Transactional` 메서드는 RDB 저장만 수행하고 반환. 호출자(Controller 또는 Facade)가 커밋 완료 후 Redis 동기화 메서드를 별도 호출.
- 장점: 가장 명시적. 트랜잭션 경계가 코드에서 시각적으로 드러남.
- 단점: Controller 또는 Facade 계층에 동기화 호출 책임이 넘어감. 호출 누락 위험.
- 구현 난이도: 하

### 1-3. 선택 및 구현

**대안 중 하나를 선택하고, 선택 근거를 명확히 기록한다.**

선택 기준:
- 시나리오 1(Redis 전체 실패) 대응 가능 여부
- 시나리오 2(Redis 부분 실패) 대응 가능 여부
- 커밋 전 Redis 반영(유령 데이터) 구조적 차단 여부
- 기존 코드 변경 범위
- 1인 프로젝트 구현 현실성

**구현 작업 (대안 A 선택 시 기준):**

1. `CharterPropertyWriteService`, `MonthlyPropertyWriteService`에서 Redis 동기화 호출부를 `TransactionSynchronizationManager.registerSynchronization()` 내부로 이동

```java
// 변경 전
charterRepository.save(entity);
syncRedisAfterCreate(entity);

// 변경 후
charterRepository.save(entity);
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            syncRedisAfterCreate(entity);
        }
    }
);
```

2. `syncRedisAfterCreate`, `syncRedisAfterUpdate`, `syncRedisAfterStatusChange` 내부에 try-catch 추가. Redis 실패 시 로그 기록 + 보상 전략 실행.

3. 부분 실패 보상 전략 구현:
   - Hash 저장 성공 + ZSet 실패 시 → Hash 롤백(삭제) 시도 + 실패 로그 기록
   - 또는: Redis Pipeline으로 Hash + ZSet을 묶어 일괄 실행, 전체 실패 시 재시도

4. 전체 실패 보상 전략:
   - 실패 로그 기록 (propertyId, 실패 시각, 실패 대상 키)
   - 수동 복구 가능하도록 복구 유틸 메서드 작성 (향후 스케줄러 기반 자동 복구로 확장 가능)

### 1-4. 검증

- 테스트 1: 정상 흐름 — RDB 저장 + Redis 동기화 모두 성공 확인
- 테스트 2: RDB 커밋 실패 시 Redis 동기화가 실행되지 않는지 확인
- 테스트 3: Redis 부분 실패 시 보상 로직 동작 확인
- 테스트 4: Redis 전체 실패 시 로그 기록 및 RDB 데이터 보존 확인

### 1-5. 설계 결정 문서화

`docs/` 디렉토리에 F008 설계 결정 문서 작성.  
내용: 문제 정의 (2가지 시나리오 + 제외 시나리오 근거) → 대안 3개 비교표 → 선택 근거 → 검증 결과.  
이 문서가 포트폴리오 페이지의 원본 소재가 된다.

---

## 2. F006 — 사용자 매물 등록 시 중복 감지 (락 전략 비교)

### 핵심 의사결정 질문
> "두 사용자가 동일 매물을 동시에 등록할 때, 정확히 하나만 성공시키기 위해 어떤 동시성 제어 전략이 적합한가?"

### 2-1. 현재 문제 분석 및 문서화

현재 코드의 TOCTOU 레이스:
```java
// CharterPropertyWriteService.createProperty()
Optional<PropertyCharterEntity> existing = charterRepository.findById(propertyId);
if (existing.isPresent()) {
    throw new DuplicatePropertyException(...);
}
// ← Thread B도 여기까지 통과 가능 (findById 시점에 둘 다 "없음")
charterRepository.save(entity);  // Thread A 저장
// Thread B도 save() 시도 → PK 충돌 발생 (비정상 예외)
```

Thread A와 B가 동시에 findById를 호출하면 둘 다 "없음"을 받고, 둘 다 save를 시도한다.  
현재 코드에서는 PK 유니크 제약에 의해 후착 save()가 `DataIntegrityViolationException`을 던지지만, 이 예외를 catch하고 있지 않아 500 에러가 반환된다.

### 2-2. 대안 비교 분석

**대안 A: PK 유니크 제약 의존 (catch + 변환)**
- 동작: 현재 findById 사전 체크를 유지하되, save() 시 `DataIntegrityViolationException` 발생 시 이를 catch하여 `DuplicatePropertyException`으로 변환
- 장점: 추가 락 없음. 성능 오버헤드 최소. Oracle이 PK 제약으로 원자성 보장.
- 단점: 예외 제어 흐름에 비즈니스 로직 의존. DataIntegrityViolationException의 원인이 PK 중복인지 다른 제약인지 구분 필요.
- 선착 보장: Oracle PK INSERT 순서에 의해 자연스럽게 보장.

**대안 B: SELECT FOR UPDATE (비관적 락)**
- 동작: 매물 존재 확인 시 `@Lock(LockModeType.PESSIMISTIC_WRITE)` + findById 사용. 해당 행(또는 gap)에 락.
- 장점: 확인과 삽입 사이의 gap 원천 차단. 의도가 코드에서 명시적.
- 단점: 신규 INSERT의 경우 "존재하지 않는 행"에 대한 락이므로 Oracle의 gap lock 동작 확인 필요. INSERT-INSERT 충돌에서 SELECT FOR UPDATE가 실효적인지 Oracle 환경에서 검증 필요. 락 대기로 인한 응답 시간 증가.
- 주의: Oracle은 MySQL과 달리 gap lock이 없으므로, SELECT FOR UPDATE로 "존재하지 않는 행"을 잠글 수 없다. 따라서 이 대안은 Oracle 환경에서 INSERT-INSERT 동시성에 비효과적일 수 있다.

**대안 C: 애플리케이션 레벨 분산 락 (Redis SETNX 등)**
- 동작: save() 전에 Redis에 `lock:property:{propertyId}`를 SETNX로 획득. 획득 실패 시 중복 안내.
- 장점: DB 독립적. 락 범위 정밀 제어 가능.
- 단점: Redis 자체 장애 시 락 기능 상실. 락 TTL 관리 필요. 시스템 복잡도 증가. 현재 시스템 규모 대비 과잉 설계.

### 2-3. 선택 및 구현

**선택 기준:**
- Oracle 환경에서의 실효성
- 현재 시스템 규모(사용자 소수, 동시 등록 빈도 극히 낮음) 대비 적정성
- 구현 복잡도
- 선착 보장 명확성

**구현 작업 (대안 A 선택 시 기준):**

1. `CharterPropertyWriteService.createProperty()`, `MonthlyPropertyWriteService.createProperty()`에서 save() 호출부를 try-catch로 감싸기

```java
try {
    charterRepository.save(entity);
} catch (DataIntegrityViolationException e) {
    // PK 중복에 의한 충돌인지 확인
    throw new DuplicatePropertyException(
        "동시 등록 충돌: 동일 매물이 다른 사용자에 의해 먼저 등록되었습니다. propertyId=" + propertyId, e);
}
```

2. 기존 findById 사전 체크는 유지 (일반적인 중복 시도를 DB 접근 전에 빠르게 거부하는 1차 필터 역할)

3. `DuplicatePropertyException`에 기존 매물의 데이터 출처(BATCH/USER) 정보를 포함하여, 사용자에게 구분된 안내 메시지 반환

4. findById에서 발견된 기존 매물의 dataSource를 확인하여 분기:
   - BATCH → "이 매물은 국토교통부 실거래 데이터에 이미 등록된 매물입니다"
   - USER → "동일한 매물이 다른 사용자에 의해 이미 등록되어 있습니다"

### 2-4. 검증

- 테스트 1: 정상 등록 (중복 없음) → 성공
- 테스트 2: 이미 존재하는 PK로 등록 시도 → DuplicatePropertyException + 출처별 메시지 확인
- 테스트 3: 동시 등록 시뮬레이션 (2개 스레드가 동일 propertyId로 동시 save) → 하나만 성공, 나머지 DuplicatePropertyException
- 테스트 4: 배치 매물 존재 상태에서 동일 PK 사용자 등록 → "국토교통부" 안내 메시지

### 2-5. 설계 결정 문서화

`docs/` 디렉토리에 F006 설계 결정 문서 작성.  
내용: TOCTOU 문제 정의 → 대안 3개 비교 (Oracle 환경 특성 포함) → 선택 근거 → 검증 결과.

---

## 3. F009 — 정규화 경계값(bounds) 원자적 갱신

### 핵심 의사결정 질문
> "BoundsUpdater.tryExtend()의 HGET→비교→HSET 비원자적 시퀀스가 동시 매물 등록 환경에서 bounds 정합성을 어떻게 훼손하는가? 이를 원자적으로 보장하기 위해 어떤 전략이 적합한가?"

### 3-0. 전제 재검증 — Sorted Set Score의 실제 용도

Sorted Set의 Score에는 정규화된 추천 점수가 아니라 원시 값(전세금, 평수)이 저장되어 있다.

```java
// CharterPropertyWriteService.syncRedisAfterCreate()
redisHandler.redisTemplate.opsForZSet().add(
    "idx:charterPrice:" + districtName, propertyId,
    entity.getDeposit().doubleValue());      // ← 전세금 원시 값
redisHandler.redisTemplate.opsForZSet().add(
    "idx:area:" + districtName + ":전세", propertyId,
    entity.getAreaInPyeong().doubleValue());  // ← 평수 원시 값
```

정규화 점수 계산은 추천 조회 시점에 CharterRecommendationService가 수행한다.

```java
// CharterRecommendationService.calculateCharterPropertyScores()
ScoreNormalizationBounds districtBounds = getCharterBoundsFromRedis(districtName);
double priceScore = calculatePriceScore(propertyDetail.getDeposit(), districtBounds);
double spaceScore = calculateSpaceScore(propertyDetail.getAreaInPyeong(), districtBounds);
```

따라서 bounds가 변경되어도 Sorted Set Score는 변하지 않으며, 다음 추천 요청이 갱신된 bounds를 읽어 실시간으로 정규화 점수를 계산한다. "저장된 Score를 재계산"하는 Write 경로 작업은 존재하지 않으므로, ScoreRecalculationService·ForkJoinPool 병렬 재계산·더블 버퍼링/Read-Write Lock은 적용 대상이 없다.

실제로 존재하는 문제는 BoundsUpdater.tryExtend()의 비원자적 갱신 시퀀스에 한정된다.

### 3-1. 현재 문제 분석 및 문서화

**BoundsUpdater.tryExtend()의 비원자적 시퀀스:**

```java
HashOperations ops = redisHandler.redisTemplate.opsForHash();
Object minRaw = ops.get(boundsKey, minField);   // ① HGET
Object maxRaw = ops.get(boundsKey, maxField);   // ② HGET
if (value < currentMin) {
    ops.put(boundsKey, minField, ...);           // ③ HSET
}
if (value > currentMax) {
    ops.put(boundsKey, maxField, ...);           // ④ HSET
}
```

①~④ 사이에 최대 4회의 독립된 Redis 커맨드가 순차 실행되며, 이 구간은 원자적이지 않다.

**문제 시나리오 1 — 동시 등록에 의한 bounds 덮어쓰기 (Write-Write 경합)**

```
시점 T0: bounds = { minPrice: 15000 }

Thread A: 전세금 12000 매물 등록
Thread B: 전세금 10000 매물 등록

실행 순서가 T1(A읽기)→T2(B읽기)→T4(B쓰기=10000)→T3(A쓰기=12000) 이면:
결과: minPrice = 12000 (정답: 10000) — Thread B의 더 작은 값이 소실됨
```

**문제 시나리오 2 — bounds 부분 갱신 중 추천 요청의 중간 상태 읽기 (Write-Read 경합)**

```
Thread W: ③에서 minPrice=12000 갱신 완료, ④에서 maxArea=45 갱신 전
Thread R: entries(boundsKey) 호출 → minPrice는 새 값, maxArea는 이전 값

Thread R은 부분 갱신된 bounds로 정규화 점수를 계산한다.
```

**영향 범위:** Sorted Set Score가 원시 값이므로 "잘못된 Score가 영구 저장"되는 상황은 없다. bounds 부정확은 해당 시점의 추천 요청 정규화 점수에만 일시적으로 영향을 미친다. 단, 시나리오 1에서 소실된 min 값보다 더 작은 매물이 등록되기 전까지 bounds는 부정확한 상태로 유지된다.

### 3-2. 의사결정: bounds 갱신 원자성 확보 방식

**대안 A-1: WATCH + MULTI/EXEC (낙관적 락)**

동작: WATCH로 bounds 키를 감시한 뒤 HGET → 비교 → MULTI/HSET/EXEC 실행. 중간에 다른 클라이언트가 수정했으면 EXEC 실패 → 재시도.

- 장점: Redis 기본 명령만으로 구현.
- 단점: 재시도 루프 필요. Spring Data Redis에서 SessionCallback 패턴 필요(코드 복잡도 증가). 네트워크 왕복 최소 3회.

**대안 A-2: 현재 방식 유지 + 주기적 전체 재산출**

동작: tryExtend()의 비원자성을 허용하되, 배치 스케줄러가 주기적으로 RDB 실 데이터 기반 bounds를 전체 재산출.

- 장점: 구현 변경 없음. 기존 배치 인프라 활용.
- 단점: 재산출 주기(현재 월 1회) 동안 부정확한 bounds 허용.

| 기준 | A-1 WATCH/MULTI/EXEC | A-2 현행 유지 |
|------|----------------------|---------------|
| Write-Write 경합 해소 | 재시도로 해소 | 미해소 |
| Write-Read 경합 해소 | 부분 해소 | 미해소 |
| 재시도 루프 | 필요 | 해당 없음 |
| 네트워크 왕복 | 최소 3회 | 최대 4회 |
| 구현 복잡도 | SessionCallback + 재시도 루프 | 변경 없음 |

### 3-3. 선택 및 구현

**대안 중 하나를 선택하고, 선택 근거를 명확히 기록한다.**

선택 기준:
- 시나리오 1(Write-Write 경합) 해소 가능 여부
- 시나리오 2(Write-Read 경합) 해소 가능 여부
- 재시도 루프 필요 여부
- 네트워크 왕복 횟수
- 현재 시스템 구성(단일 Redis 인스턴스, Spring Data Redis) 대비 구현 적합성

**구현 작업 (대안 A-1 선택 시 기준):**

1. BoundsUpdater.tryExtend()를 SessionCallback 기반 WATCH + MULTI/EXEC 구조로 교체
2. EXEC 실패 시 제한 횟수 내 재시도 루프 구현
3. 재시도 초과 시 로그 기록 + 주기적 전체 재산출 대상으로 등록

**호출부 확인 (공통):**

syncRedisAfterCreate(), syncRedisAfterUpdate()에서 boundsUpdater.tryExtend() 호출 시그니처·반환값 의미 모두 동일하므로 호출부 코드 변경 불필요.

### 3-4. 검증

- 테스트 1: 정상 흐름 — 최초 생성, 확장(value < min), 무변경(min ≤ value ≤ max) 3가지 케이스.
- 테스트 2: 동시 등록 시 bounds 정합성 — 2개 이상 스레드가 동일 지역구에 서로 다른 가격 매물을 동시 등록. 선택한 대안 적용 전/후 비교.
- 테스트 3: Write-Read 경합 — 매물 등록(bounds 갱신)과 추천 조회(bounds 읽기)를 동시 실행. 조회 측이 부분 갱신된 중간 상태를 읽지 않는지 확인.

### 3-5. 설계 결정 문서화

`docs/` 디렉토리에 F009 설계 결정 문서 작성.

문서 구조:
1. **전제 재검증 기록**: 초기 계획에서 전제한 아키텍처와 실제 코드의 불일치 발견 과정. 이 불일치에 의해 폐기된 항목의 논리.
2. **실제 문제 정의**: BoundsUpdater.tryExtend()의 비원자적 시퀀스로 인한 2가지 경합 시나리오.
3. **대안 3개 비교표**.
4. **선택 근거**: 시스템 환경 기반.
5. **검증 결과**: 동시 등록 테스트 전/후 비교.

### 3-6. CPU 바운드 구간 이관 — 추천 조회 경로 병렬화

초기 계획에서 F009 범위로 포함했던 "CPU 바운드 재계산의 ForkJoinPool 병렬화"는 Write 경로에 재계산 작업이 존재하지 않으므로 F009에서 분리한다.

실제 CPU 바운드 구간은 CharterRecommendationService.calculateCharterPropertyScores()의 지역구별 점수 계산 루프이다:

```
지역구 1개당:
  ① Redis 조회: getCharterBoundsFromRedis()                    — I/O (1 RTT)
  ② Redis 조회: getMultipleCharterPropertiesFromRedis()         — I/O (Pipeline 1 RTT)
  ③ 메모리 내 루프: 매물 N건 × (정규화 + 가중치 + 합산)          — CPU
  ④ 정렬: propertiesWithScores.sort()                           — CPU
```

이 구간의 병렬화는 "추천 조회 경로 최적화"라는 별도 항목으로 정의한다. 기존 포트폴리오의 Kakao API 병렬화(I/O 바운드 → CompletableFuture 선택, ForkJoinPool 기각)와 워크로드 특성이 다르므로(I/O + CPU 혼합), 동일 기술의 적합성을 재평가하는 설계 판단 소재가 된다.

구체적인 의사결정 구조, 대안 비교, 구현 작업은 별도 문서에서 정의한다.

---

## 4. F007 — 배치-사용자 데이터 충돌 처리 (임계값 기반 머지)

### 핵심 의사결정 질문
> "배치 매물과 사용자 매물이 동일 MD5로 충돌할 때, 어느 쪽 가격을 신뢰하는가? 그 기준은 무엇인가?"

### 4-1. 기존 배치 코드 파악

**사전 작업:** 기존 `BatchScheduler` (또는 배치 관련 컴포넌트)의 UPSERT 로직을 확인한다.

확인할 내용:
- 배치 매물 저장 시 기존 레코드 존재 여부 확인 방식
- UPSERT 전략 (INSERT ON CONFLICT? MERGE? findById + save?)
- 트랜잭션 범위
- DATA_SOURCE 컬럼 처리 방식

### 4-2. 임계값 산출 기준 설계

1. 국토부 API 실데이터에서 동일 매물의 전월 대비 가격 변동률 분포 추출
   - 기존 PROPERTIES_CHARTER/MONTHLY 테이블에 동일 PROPERTY_ID의 DEAL_DATE별 가격 변동 확인
   - 또는 배치 적재 이력이 있다면 이력 데이터 활용

2. 정상 변동 범위 정의
   - 예: 전세금 전월 대비 변동률 분포의 95 백분위수 → 이 값을 임계값으로 설정
   - 구체적 수치는 실데이터 분석 후 확정

3. 임계값을 설정 파일(application.yml) 또는 상수로 관리하여 조정 가능하게 구성

### 4-3. 머지 로직 구현

**대상:** 배치 스케줄러의 UPSERT 시점에 개입

**구현 흐름:**

```
배치 매물 1건 적재 시:
1. MD5 해시로 기존 레코드 조회
2. 기존 레코드 없음 → 기존 배치 동작 그대로 INSERT
3. 기존 레코드 있음 + DATA_SOURCE = BATCH → 기존 배치 동작 그대로 UPSERT (기존 동작 무변경)
4. 기존 레코드 있음 + DATA_SOURCE = USER → 충돌 발생!
   4-1. 가격 차이율 산출: |배치가격 - 사용자가격| / 사용자가격 × 100
   4-2. 임계값 이내 → 최신 데이터(타임스탬프 비교) 메인 가격 채택
   4-3. 임계값 초과 → 국토부 데이터 메인 가격 채택
   4-4. 사용자 원본 가격을 USER_PROPOSED_DEPOSIT (/ USER_PROPOSED_MONTHLY_RENT) 필드에 보존
   4-5. DATA_SOURCE = MERGED로 변경
   4-6. Redis 검색 인덱스 동기화 (F008 활용)
5. 머지 로그 기록: propertyId, 가격 차이율, 적용 분기(최신성/신뢰성), 머지 결과
```

**구현 파일:**
1. `MergeService` (또는 `BatchMergeProcessor`) 신규 클래스 생성
2. 배치 스케줄러의 UPSERT 지점에서 `MergeService` 호출 삽입
3. `USER_PROPOSED_DEPOSIT`, `USER_PROPOSED_MONTHLY_RENT` 필드 활용 (Entity에 이미 선제 반영됨)

### 4-4. 동시 쓰기 (배치 + 사용자 동시 등록) 대응

발생 빈도: 배치 월 1회(새벽 4시) + 사용자 수시 → 극히 낮음.

대응 전략:
- F006에서 구현한 PK 유니크 제약 기반 충돌 감지가 동일하게 적용됨
- 배치 UPSERT와 사용자 INSERT가 동시에 같은 PK를 생성하면, PK 제약에 의해 후착 실패
- 후착 실패 시: 배치는 재시도(기존 UPSERT 로직), 사용자는 DuplicatePropertyException

이 부분은 구체적 구현보다 **"발생 빈도와 영향도를 분석한 결과 별도 처리 불필요"라는 설계 판단 자체**가 포트폴리오 소재가 된다.

### 4-5. 검증

- 테스트 1: 배치 매물과 동일 MD5 사용자 매물 충돌 → 임계값 이내 → 최신 데이터 채택 확인
- 테스트 2: 임계값 초과 → 국토부 데이터 채택 확인
- 테스트 3: 머지 후 DATA_SOURCE = MERGED, USER_PROPOSED_DEPOSIT 보존 확인
- 테스트 4: 머지 후 Redis 검색 인덱스 동기화 확인
- 테스트 5: 리뷰 FK 참조 무결성 유지 확인 (PROPERTY_ID 변경 없으므로 자연 유지)

### 4-6. 설계 결정 문서화

`docs/` 디렉토리에 F007 설계 결정 문서 작성.  
내용: 충돌 시나리오 정의 → 머지 정책 대안 비교 (국토부 무조건 우선 vs 최신성 우선 vs 하이브리드) → 임계값 산출 근거 → 동시 쓰기 빈도 분석 → 검증 결과.

---

## 5. 전체 작업 순서 요약

```
0. 사전 정리
   0-1. @Transactional 주석 해제
   0-2. execption → exception 리네이밍
   0-3. 공통 코드 추출 (SeoulDistrictCodes, PropertyConversionUtil)
   0-4. verifyOwnership 주석 정합성 확보
   ↓
1. F008 (검색 인덱스 동기화)
   1-1. 현재 문제 3가지 시나리오 문서화
   1-2. 대안 3개 비교 분석
   1-3. 선택 + 구현 (afterCommit + 부분실패 보상)
   1-4. 검증
   1-5. 설계 결정 문서 작성
   ↓
2. F006 (중복 감지)
   2-1. TOCTOU 문제 문서화
   2-2. 대안 3개 비교 (Oracle 환경 특성 반영)
   2-3. 선택 + 구현 (PK 제약 catch + 출처별 메시지)
   2-4. 검증 (동시 등록 스레드 테스트)
   2-5. 설계 결정 문서 작성
   ↓
3. F009 (bounds 원자적 갱신)
   3-0. 전제 재검증 — Sorted Set Score 실제 용도 확인
   3-1. BoundsUpdater 비원자성 문제 2가지 시나리오 문서화
   3-2. 대안 2개 비교 (WATCH+MULTI / 현행 유지)
   3-3. 선택 + 구현
   3-4. 검증 (동시 등록 bounds 정합성 + Write-Read 경합)
   3-5. 설계 결정 문서 작성
   3-6. CPU 바운드 구간 → 추천 조회 경로 병렬화 별도 항목으로 이관
   ↓
4. F007 (배치-사용자 머지)
   4-1. 기존 배치 코드 파악
   4-2. 임계값 산출 기준 설계 (실데이터 분석)
   4-3. 머지 로직 구현
   4-4. 동시 쓰기 대응 (설계 판단 문서화)
   4-5. 검증
   4-6. 설계 결정 문서 작성
```

---

## 6. 각 F에서 포트폴리오 페이지로의 변환 프레임

각 설계 결정 문서를 포트폴리오 1페이지로 압축할 때의 구조:

```
[비즈니스 맥락]  왜 이 문제가 발생하는가 (기술 용어 없이, 사용자/서비스 관점)
      ↓
[설계 선택지]   어떤 대안이 있었고, 각각의 트레이드오프는 무엇인가
      ↓
[선택 근거]    왜 이것을 선택했는가 (환경 제약, 비즈니스 우선순위 기반)
      ↓
[검증 결과]    선택이 올바른지 어떻게 확인했는가 (테스트, 측정)
```

예시 (F008):
- 비즈니스 맥락: "사용자가 매물을 등록하면 즉시 추천 검색에 반영되어야 하지만, 저장소가 두 곳(DB + 캐시)이라 한쪽만 성공하면 등록한 매물이 검색에 안 나오는 문제가 생긴다"
- 설계 선택지: "DB 저장과 캐시 갱신을 어떤 순서로, 어떤 보장 수준으로 할 것인가"
- 선택 근거: "DB 확정 후 캐시 갱신 방식 선택. 이유: 캐시에만 있고 DB에 없는 유령 매물이 검색에 노출되는 것이, DB에 있는데 캐시에 없어서 검색 누락되는 것보다 더 위험하다"
- 검증 결과: "DB 커밋 실패 시 캐시 갱신 미실행 확인, 캐시 부분 실패 시 보상 삭제 동작 확인"

예시 (F009):
- 비즈니스 맥락: "매물이 등록되면 해당 지역구의 가격 범위(최솟값·최댓값)가 갱신되는데, 두 사용자가 동시에 매물을 등록하면 범위 갱신이 꼬여서 추천 점수의 기준이 되는 범위 값이 틀어질 수 있다"
- 설계 선택지: "범위 갱신 로직(읽기→비교→쓰기)을 어떻게 원자적으로 만들 것인가"
- 선택 근거: (대안 선택 후 기록)
- 검증 결과: (대안 적용 후 동시 등록 테스트 전/후 비교)
- 추가 소재: "초기 계획에서 전제한 아키텍처와 실제 코드의 불일치를 발견하여 작업 범위를 축소한 판단 과정"