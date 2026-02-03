# Redis Pipeline 미적용 구간 병목 해결 계획서

## 문서 정보

| 항목 | 내용 |
|------|------|
| **대상 프로젝트** | Wherehouse (서울시 부동산 추천 시스템) |
| **대상 병목** | 2.3.1 Redis Pipeline 미적용 구간 (RTT 낭비) |
| **작성 목적** | 병목 해결 및 포트폴리오 보고서 수록 |
| **관련 문서** | `wherehouse_병목_현상_예상_지점_정리.docx` |

---

## 1. 프로젝트 컨텍스트

### 1.1 시스템 개요

Wherehouse는 서울시 부동산 매물에 대한 안전성과 편의성 점수를 분석하여 사용자에게 맞춤형 추천을 제공하는 Spring Boot 기반 웹 애플리케이션이다.

**기술 스택**:
- Backend: Java 17, Spring Boot 3.x, Spring Data JPA
- Database: Oracle (RDB), Redis (Cache)
- External API: 공공데이터포털 부동산 API, Kakao Maps API
- Batch: Spring Scheduler 기반 데이터 수집/동기화

### 1.2 추천 서비스 흐름

```
사용자 요청 (보증금/월세/평수 조건)
    ↓
MonthlyRecommendationService.getMonthlyDistrictRecommendations()
    ↓
S-01: performMonthlyStrictSearch() ─→ 25개 지역구 순회
    ↓                                      ↓
    │                          findValidMonthlyPropertiesInDistrict()
    │                                      ↓
    │                          Redis ZSet 3회 조회 (보증금/월세/평수)
    │                                      ↓
    │                          Java 측 교집합 계산
    ↓
S-02~S-03: 폴백 조건 판단 및 확장 검색
    ↓
S-04: calculateMonthlyPropertyScores() ─→ 하이브리드 점수 계산
    ↓
S-05: calculateDistrictScoresAndSort() ─→ 지역구별 정렬
    ↓
S-06: generateMonthlyFinalResponse() ─→ 최종 응답 생성
```

---

## 2. 병목 현상 상세 분석

### 2.1 병목 식별 정보 (문서 2.3.1절 원문)

**발생 위치**:
- `MonthlyRecommendationService.java`: `findValidMonthlyPropertiesInDistrict()`

**문제 코드** (문서 기재):
```java
// 1. 보증금 범위 검색 (동기 호출)
Set<Object> depositValidObjects = redisTemplate.opsForZSet().rangeByScore(...);

// 2. 월세금 범위 검색 (동기 호출)
Set<Object> monthlyRentValidObjects = redisTemplate.opsForZSet().rangeByScore(...);

// 3. 평수 범위 검색 (동기 호출)
Set<Object> areaValidObjects = redisTemplate.opsForZSet().rangeByScore(...);
```

**기술적 원인** (문서 기재):
- **네트워크 왕복 시간(RTT) 누적**: Redis 명령어를 3번 연속 동기식으로 호출한다. 요청 → 응답대기 → 요청 → 응답대기 순으로 진행되며, Redis 서버가 Microsecond 단위로 빨라도 네트워크 RTT(Millisecond 단위)가 3번 누적된다. 추천 서비스가 25개 자치구를 순회하면 최악의 경우 25 × 3 = 75번의 불필요한 네트워크 왕복이 발생한다.
- **커넥션 점유 시간 증가**: 응답 대기 중 스레드가 블로킹(Blocking)되므로 처리량이 떨어지고, Redis 커넥션 풀의 회전율도 낮아진다.

**측정 지표** (문서 기재):
- Redis Command Latency vs Method Execution Time 비교
- 총 Command Latency 대비 메소드 총 시간 비율 (I/O 비율)

---

### 2.2 실제 코드 분석

**파일**: `MonthlyRecommendationService.java` (206-241줄)

```java
/**
 * 월세 매물 검색 - 3개 인덱스 교집합
 */
private List<String> findValidMonthlyPropertiesInDistrict(String district, MonthlyRecommendationRequestDto request) {
    try {
        // 1. 보증금
        String depositIndexKey = "idx:deposit:" + district;
        Set<Object> depositValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(depositIndexKey, request.getBudgetMin(), request.getBudgetMax());

        if (depositValidObjects == null || depositValidObjects.isEmpty()) return Collections.emptyList();
        Set<String> depositValidIds = depositValidObjects.stream().map(Object::toString).collect(Collectors.toSet());

        // 2. 월세금
        String monthlyRentIndexKey = "idx:monthlyRent:" + district + ":월세";
        Set<Object> monthlyRentValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(monthlyRentIndexKey, request.getMonthlyRentMin(), request.getMonthlyRentMax());

        if (monthlyRentValidObjects == null || monthlyRentValidObjects.isEmpty()) return Collections.emptyList();
        Set<String> monthlyRentValidIds = monthlyRentValidObjects.stream().map(Object::toString).collect(Collectors.toSet());

        // 3. 평수
        String areaIndexKey = "idx:area:" + district + ":월세";
        Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());

        if (areaValidObjects == null || areaValidObjects.isEmpty()) return Collections.emptyList();
        Set<String> areaValidIds = areaValidObjects.stream().map(Object::toString).collect(Collectors.toSet());

        // 교집합
        depositValidIds.retainAll(monthlyRentValidIds);
        depositValidIds.retainAll(areaValidIds);
        return new ArrayList<>(depositValidIds);

    } catch (Exception e) {
        log.debug("월세 매물 검색 중 오류 - 지역구: {}", district, e);
        return Collections.emptyList();
    }
}
```

**호출 컨텍스트**: `performMonthlyStrictSearch()` (86-114줄)

```java
private Map<String, List<String>> performMonthlyStrictSearch(MonthlyRecommendationRequestDto request,
                                                             List<String> targetDistricts) {
    log.info("S-01: 월세 매물 검색 시작 - 대상: {}", targetDistricts.size());

    // 안전성 점수 필터링 (선택적)
    List<String> filteredDistricts = targetDistricts;
    if (request.getMinSafetyScore() != null && request.getMinSafetyScore() > 0) {
        filteredDistricts = targetDistricts.stream()
                .filter(district -> {
                    double districtSafetyScore = getDistrictSafetyScoreFromRedis(district);
                    return districtSafetyScore >= request.getMinSafetyScore();
                })
                .collect(Collectors.toList());
    }

    Map<String, List<String>> result = new HashMap<>();
    int totalFound = 0;

    // ★ 병목 지점: 25개 지역구 순차 처리
    for (String district : filteredDistricts) {
        List<String> validProperties = findValidMonthlyPropertiesInDistrict(district, request);

        if (!validProperties.isEmpty()) {
            result.put(district, validProperties);
            totalFound += validProperties.size();
        }
    }

    log.info("월세 매물 검색 완료: 총 {}개 매물 ID 발견 ({}개 지역구)", totalFound, result.size());
    return result;
}
```

---

### 2.3 실행 흐름 시각화

**현재 구조의 시간 흐름**:

```
[클라이언트 요청]
     │
     ▼
performMonthlyStrictSearch()
     │
     ├─────────────────────────────────────────────────────────────────┐
     │  for (district : 25개 지역구) {                                  │
     │      │                                                          │
     │      ▼                                                          │
     │  findValidMonthlyPropertiesInDistrict(district)                 │
     │      │                                                          │
     │      ├─ [RTT #1] rangeByScore(idx:deposit:강남구, ...)          │
     │      │      └─→ 네트워크 전송 → Redis 처리 → 응답 대기 (블로킹)    │
     │      │                                                          │
     │      ├─ [RTT #2] rangeByScore(idx:monthlyRent:강남구:월세, ...)  │
     │      │      └─→ 네트워크 전송 → Redis 처리 → 응답 대기 (블로킹)    │
     │      │                                                          │
     │      ├─ [RTT #3] rangeByScore(idx:area:강남구:월세, ...)         │
     │      │      └─→ 네트워크 전송 → Redis 처리 → 응답 대기 (블로킹)    │
     │      │                                                          │
     │      └─ retainAll() × 2 (Java 측 교집합)                         │
     │  }                                                              │
     └─────────────────────────────────────────────────────────────────┘
     │
     ▼
[결과 반환]

총 RTT 횟수: 25개 지역구 × 3회/지역구 = 최대 75회
```

---

### 2.4 블로킹 메커니즘 상세

`redisTemplate.opsForZSet().rangeByScore()`는 **동기 블로킹 호출**이다.

Spring Data Redis의 `RedisTemplate`은 내부적으로 Lettuce(또는 Jedis) 동기 API를 사용한다. 호출 시 다음 시퀀스가 발생한다:

```
Thread 
   → RedisTemplate.opsForZSet() 
   → LettuceConnection.zRangeByScore() 
   → Lettuce sync command 
   → Netty EventLoop (실제 I/O) 
   → 응답 대기 (Thread.park())
   → 응답 수신 
   → Thread 재개
```

**블로킹의 실체**: 호출 스레드가 `LockSupport.park()`로 중단되어 응답을 기다린다. 이 기간 동안 해당 스레드는 다른 작업을 수행할 수 없다.

**Thread Dump 확인 시 예상 패턴**:
```
"http-nio-8080-exec-1" #30 prio=5 os_prio=0 waiting on condition
   at sun.misc.Unsafe.park(Native Method)
   - parking to wait for <0x00000007173b2d98> (a java.util.concurrent.CountDownLatch$Sync)
   at io.lettuce.core.protocol.AsyncCommand.await(AsyncCommand.java:83)
   ...
```

---

## 3. 개선 범위 정의

### 3.1 개선 수준 구분

현재 구조에서 병렬화/최적화를 적용할 수 있는 지점이 **두 곳**이다:

| 수준 | 대상 | 현재 상태 | 개선 방식 | 효과 |
|------|------|----------|----------|------|
| **수평적 (Intra-District)** | 단일 지역구 내 3개 ZSet 조회 | 3회 순차 RTT | Redis Pipeline | RTT 3회 → 1회 |
| **수직적 (Inter-District)** | 25개 지역구 조회 | 순차 블로킹 루프 | CompletableFuture | 순차 25회 → 병렬 N회 |

### 3.2 개선 계획 범위

| 구분 | 대상 | 목표 | 상태 | 비고 |
|------|------|------|------|------|
| **1차 (현재 범위)** | 단일 지역구 내 3회 RTT | Pipeline으로 1회 RTT로 감소 | 구현 대상 | 2.3.1 병목 직접 해결 |
| **2차 (계획 포함)** | 25개 지역구 순차 블로킹 | CompletableFuture 병렬화 | 계획만 | 범위 외, 문서화 |
| **차후 가능성** | 서버 측 교집합 연산 | Lua Script 적용 | 참고만 | 추가 학습 필요 |

---

## 4. 1차 개선: Redis Pipeline 적용

### 4.1 Pipeline 동작 원리

**Redis Pipeline이란**:
- 여러 명령을 **하나의 네트워크 패킷**으로 묶어서 전송
- 서버는 모든 명령을 순차 처리 후 결과를 한 번에 반환
- **RTT가 1회로 감소**

**Before (순차 호출)**:
```
[CMD1] → RTT → [Response1] → [CMD2] → RTT → [Response2] → [CMD3] → RTT → [Response3]
총 시간: 3 × (Command처리 + RTT)
```

**After (Pipeline)**:
```
[CMD1, CMD2, CMD3] → RTT → [Response1, Response2, Response3]
총 시간: 3 × Command처리 + 1 × RTT
```

**시각화**:
```
Before:
Thread ──→ [CMD1] ──→ 네트워크 ──→ Redis ──→ 응답 ──→ [대기 해제]
       ──→ [CMD2] ──→ 네트워크 ──→ Redis ──→ 응답 ──→ [대기 해제]
       ──→ [CMD3] ──→ 네트워크 ──→ Redis ──→ 응답 ──→ [대기 해제]
       
총 대기 시간: 3 × RTT

After:
Thread ──→ [CMD1, CMD2, CMD3] ──→ 네트워크 ──→ Redis ──→ [응답1, 응답2, 응답3] ──→ [대기 해제]

총 대기 시간: 1 × RTT
```

---

### 4.2 현재 코드 (Before)

```java
// MonthlyRecommendationService.java (206-241줄)
private List<String> findValidMonthlyPropertiesInDistrict(String district, 
                                                          MonthlyRecommendationRequestDto request) {
    try {
        // RTT #1: 보증금 범위 검색
        String depositIndexKey = "idx:deposit:" + district;
        Set<Object> depositValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(depositIndexKey, request.getBudgetMin(), request.getBudgetMax());

        if (depositValidObjects == null || depositValidObjects.isEmpty()) 
            return Collections.emptyList();  // Early Termination #1
        Set<String> depositValidIds = depositValidObjects.stream()
                .map(Object::toString).collect(Collectors.toSet());

        // RTT #2: 월세금 범위 검색
        String monthlyRentIndexKey = "idx:monthlyRent:" + district + ":월세";
        Set<Object> monthlyRentValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(monthlyRentIndexKey, request.getMonthlyRentMin(), request.getMonthlyRentMax());

        if (monthlyRentValidObjects == null || monthlyRentValidObjects.isEmpty()) 
            return Collections.emptyList();  // Early Termination #2
        Set<String> monthlyRentValidIds = monthlyRentValidObjects.stream()
                .map(Object::toString).collect(Collectors.toSet());

        // RTT #3: 평수 범위 검색
        String areaIndexKey = "idx:area:" + district + ":월세";
        Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());

        if (areaValidObjects == null || areaValidObjects.isEmpty()) 
            return Collections.emptyList();  // Early Termination #3
        Set<String> areaValidIds = areaValidObjects.stream()
                .map(Object::toString).collect(Collectors.toSet());

        // Java 측 교집합
        depositValidIds.retainAll(monthlyRentValidIds);
        depositValidIds.retainAll(areaValidIds);
        return new ArrayList<>(depositValidIds);

    } catch (Exception e) {
        log.debug("월세 매물 검색 중 오류 - 지역구: {}", district, e);
        return Collections.emptyList();
    }
}
```

---

### 4.3 개선 코드 (After)

```java
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline을 적용한 월세 매물 검색 메소드
 * 
 * 개선 사항:
 * - 3회의 순차적 RTT를 1회로 감소
 * - 동일한 비즈니스 로직 유지 (교집합 계산)
 * 
 * 트레이드오프:
 * - Early Termination 불가 (3개 조회 모두 실행 후 빈 결과 체크)
 * - 대부분의 지역구에서 결과가 있는 경우 유리
 */
private List<String> findValidMonthlyPropertiesInDistrictWithPipeline(
        String district, 
        MonthlyRecommendationRequestDto request) {
    
    // 1. Redis Key 구성
    String depositKey = "idx:deposit:" + district;
    String monthlyRentKey = "idx:monthlyRent:" + district + ":월세";
    String areaKey = "idx:area:" + district + ":월세";
    
    try {
        // 2. Pipeline 실행: 3개 명령을 하나의 네트워크 패킷으로 전송
        List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(
            (RedisCallback<Object>) connection -> {
                // 명령 #1: 보증금 범위 검색
                connection.zRangeByScore(
                    depositKey.getBytes(StandardCharsets.UTF_8), 
                    request.getBudgetMin(), 
                    request.getBudgetMax()
                );
                // 명령 #2: 월세금 범위 검색
                connection.zRangeByScore(
                    monthlyRentKey.getBytes(StandardCharsets.UTF_8), 
                    request.getMonthlyRentMin(), 
                    request.getMonthlyRentMax()
                );
                // 명령 #3: 평수 범위 검색
                connection.zRangeByScore(
                    areaKey.getBytes(StandardCharsets.UTF_8), 
                    request.getAreaMin(), 
                    request.getAreaMax()
                );
                return null;  // Pipeline에서는 반환값이 executePipelined() 결과로 수집됨
            }
        );
        
        // 3. 결과 추출 (인덱스 순서는 명령 실행 순서와 동일)
        @SuppressWarnings("unchecked")
        Set<byte[]> depositResult = (Set<byte[]>) pipelineResults.get(0);
        @SuppressWarnings("unchecked")
        Set<byte[]> rentResult = (Set<byte[]>) pipelineResults.get(1);
        @SuppressWarnings("unchecked")
        Set<byte[]> areaResult = (Set<byte[]>) pipelineResults.get(2);
        
        // 4. 빈 결과 체크 (Pipeline 이후 Early Termination)
        if (depositResult == null || depositResult.isEmpty() ||
            rentResult == null || rentResult.isEmpty() ||
            areaResult == null || areaResult.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 5. byte[] → String 변환
        Set<String> depositIds = depositResult.stream()
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .collect(Collectors.toSet());
        
        Set<String> rentIds = rentResult.stream()
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .collect(Collectors.toSet());
        
        Set<String> areaIds = areaResult.stream()
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .collect(Collectors.toSet());
        
        // 6. 교집합 계산 (기존 로직 유지)
        depositIds.retainAll(rentIds);
        depositIds.retainAll(areaIds);
        
        return new ArrayList<>(depositIds);
        
    } catch (Exception e) {
        log.error("Pipeline 월세 매물 검색 중 오류 - 지역구: {}", district, e);
        return Collections.emptyList();
    }
}
```

---

### 4.4 Early Termination 트레이드오프 분석

**현재 코드의 Early Termination**:
```java
// RTT #1 실행
if (depositValidObjects.isEmpty()) return Collections.emptyList();  // 빈 결과 시 RTT #2, #3 생략

// RTT #2 실행
if (monthlyRentValidObjects.isEmpty()) return Collections.emptyList();  // 빈 결과 시 RTT #3 생략

// RTT #3 실행
if (areaValidObjects.isEmpty()) return Collections.emptyList();
```

**Pipeline 적용 시 변화**:
```java
// 3개 명령 모두 한 번에 전송 (Early Termination 불가)
List<Object> results = executePipelined(...);

// 모든 결과 수신 후 빈 결과 체크
if (depositResult.isEmpty() || rentResult.isEmpty() || areaResult.isEmpty()) {
    return Collections.emptyList();
}
```

**트레이드오프 비교**:

| 시나리오 | Before (순차) | After (Pipeline) | 유리한 쪽 |
|----------|--------------|------------------|----------|
| 모든 결과 있음 | 3 RTT | 1 RTT | Pipeline |
| 첫 번째만 빈 결과 | 1 RTT | 1 RTT | 동일 |
| 두 번째만 빈 결과 | 2 RTT | 1 RTT | Pipeline |
| 세 번째만 빈 결과 | 3 RTT | 1 RTT | Pipeline |
| 대부분 빈 결과 | 평균 1~2 RTT | 1 RTT | 상황에 따라 |

**설계 결정 근거**:
- 현재 서비스 특성상 사용자가 조건을 입력하면 대부분의 지역구에서 매물이 검색됨
- 빈 결과가 많은 엣지 케이스는 통계 기반 적응형 전략으로 대응 가능 (차후)
- **결론: Pipeline 적용이 유리**

---

### 4.5 RedisHandler 확장 (선택적)

기존 `RedisHandler` 클래스에 Pipeline 지원 메소드를 추가할 수 있다:

```java
// RedisHandler.java 확장

/**
 * Pipeline 실행 헬퍼 메소드
 * 
 * @param callback Pipeline 내에서 실행할 Redis 명령들
 * @return 각 명령의 실행 결과 리스트 (명령 순서대로)
 */
public List<Object> executePipeline(RedisCallback<Object> callback) {
    try {
        return redisTemplate.executePipelined(callback);
    } catch (Exception e) {
        log.error("Redis Pipeline 실행 오류: {}", e.getMessage());
        throw new RuntimeException("Redis Pipeline 실행 실패", e);
    }
}
```

---

## 5. 성능 측정 방법론

### 5.1 용어 정의 수정

애플리케이션 레벨에서 측정하는 값은 순수 네트워크 RTT가 아닌, Redis 명령 실행의 전체 소요 시간이다. 따라서 용어를 다음과 같이 정의한다:

| 기존 용어 | 수정 용어 | 정의 |
|----------|----------|------|
| RTT | **Command Latency** | `rangeByScore()` 메소드 호출 시작부터 반환까지의 총 소요 시간 |
| RTT 합계 | **총 Command Latency** | 3개 Redis 명령의 Command Latency 합 |
| RTT 비율 | **I/O 비율** | (총 Command Latency / 메소드 총 시간) × 100 |
| 순수 RTT | **Network RTT** | 패킷이 네트워크를 왕복하는 순수 시간 (Wireshark로만 측정 가능) |

**Command Latency에 포함되는 비용 항목:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                    rangeByScore() 전체 실행 시간                      │
├─────────────────────────────────────────────────────────────────────┤
│  1. 클라이언트 직렬화       │ RESP 프로토콜로 명령 인코딩            │
│  2. write() syscall        │ User → Kernel 전환 + 버퍼 복사         │
│  3. TCP 송신               │ 커널 버퍼 → NIC → 네트워크              │
├─────────────────────────────┼───────────────────────────────────────┤
│  4. 네트워크 전파 (송신)    │  ┐                                     │
│  5. Redis 서버 처리         │  │ ← "순수 Network RTT"는 4+6만 해당   │
│  6. 네트워크 전파 (수신)    │  ┘                                     │
├─────────────────────────────┼───────────────────────────────────────┤
│  7. NIC → 커널 버퍼         │ 인터럽트 처리                          │
│  8. read() syscall         │ Kernel → User 전환 + 버퍼 복사         │
│  9. 클라이언트 역직렬화     │ RESP 응답 파싱 + Object 변환           │
└─────────────────────────────┴───────────────────────────────────────┘
```

---

### 5.2 측정 계층 구분

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         측정 계층 구조                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Layer 1: 애플리케이션 레벨 측정 (System.nanoTime())              │  │
│  │                                                                   │  │
│  │  측정 대상: Command Latency                                       │  │
│  │  포함 요소: 직렬화 + syscall + 네트워크 + Redis 처리 + 역직렬화   │  │
│  │  도구: Java 코드 내 System.nanoTime()                             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  Layer 2: 네트워크 레벨 측정 (Wireshark)                          │  │
│  │                                                                   │  │
│  │  측정 대상: Network RTT (패킷 송신 → 응답 수신)                   │  │
│  │  포함 요소: 네트워크 전파 + Redis 처리                            │  │
│  │  도구: Wireshark (loopback 캡처, 나노초 정밀도)                   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

### 5.3 Layer 1: 애플리케이션 레벨 측정

#### 5.3.1 Before 측정 코드 (순차 호출)

```java
/**
 * 성능 측정용 메소드 - 개선 전 코드
 * 각 Command Latency와 메소드 전체 시간을 측정하여 I/O 비율 산출
 */
private List<String> findValidMonthlyPropertiesInDistrictWithMetrics(
        String district, 
        MonthlyRecommendationRequestDto request) {
    
    long methodStart = System.nanoTime();
    long totalCommandLatency = 0;
    
    try {
        // Command #1: 보증금
        String depositIndexKey = "idx:deposit:" + district;
        long cmd1Start = System.nanoTime();
        Set<Object> depositValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(depositIndexKey, request.getBudgetMin(), request.getBudgetMax());
        long cmd1End = System.nanoTime();
        totalCommandLatency += (cmd1End - cmd1Start);
        
        if (depositValidObjects == null || depositValidObjects.isEmpty()) {
            logMetrics(district, methodStart, totalCommandLatency, 1, "EMPTY_DEPOSIT");
            return Collections.emptyList();
        }
        Set<String> depositValidIds = depositValidObjects.stream()
                .map(Object::toString).collect(Collectors.toSet());
        
        // Command #2: 월세금
        String monthlyRentIndexKey = "idx:monthlyRent:" + district + ":월세";
        long cmd2Start = System.nanoTime();
        Set<Object> monthlyRentValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(monthlyRentIndexKey, request.getMonthlyRentMin(), request.getMonthlyRentMax());
        long cmd2End = System.nanoTime();
        totalCommandLatency += (cmd2End - cmd2Start);
        
        if (monthlyRentValidObjects == null || monthlyRentValidObjects.isEmpty()) {
            logMetrics(district, methodStart, totalCommandLatency, 2, "EMPTY_RENT");
            return Collections.emptyList();
        }
        Set<String> monthlyRentValidIds = monthlyRentValidObjects.stream()
                .map(Object::toString).collect(Collectors.toSet());
        
        // Command #3: 평수
        String areaIndexKey = "idx:area:" + district + ":월세";
        long cmd3Start = System.nanoTime();
        Set<Object> areaValidObjects = redisHandler.redisTemplate.opsForZSet()
                .rangeByScore(areaIndexKey, request.getAreaMin(), request.getAreaMax());
        long cmd3End = System.nanoTime();
        totalCommandLatency += (cmd3End - cmd3Start);
        
        if (areaValidObjects == null || areaValidObjects.isEmpty()) {
            logMetrics(district, methodStart, totalCommandLatency, 3, "EMPTY_AREA");
            return Collections.emptyList();
        }
        Set<String> areaValidIds = areaValidObjects.stream()
                .map(Object::toString).collect(Collectors.toSet());
        
        // 교집합 계산
        depositValidIds.retainAll(monthlyRentValidIds);
        depositValidIds.retainAll(areaValidIds);
        
        logMetrics(district, methodStart, totalCommandLatency, 3, "SUCCESS");
        return new ArrayList<>(depositValidIds);
        
    } catch (Exception e) {
        log.error("측정 중 오류 - 지역구: {}", district, e);
        return Collections.emptyList();
    }
}
```

#### 5.3.2 After 측정 코드 (Pipeline)

```java
/**
 * 성능 측정용 메소드 - 개선 후 코드 (Pipeline)
 * 단일 Command Latency와 메소드 전체 시간을 측정하여 I/O 비율 산출
 */
private List<String> findValidMonthlyPropertiesInDistrictWithPipelineMetrics(
        String district, 
        MonthlyRecommendationRequestDto request) {
    
    long methodStart = System.nanoTime();
    
    String depositKey = "idx:deposit:" + district;
    String monthlyRentKey = "idx:monthlyRent:" + district + ":월세";
    String areaKey = "idx:area:" + district + ":월세";
    
    try {
        // Pipeline 실행 (단일 Command Latency)
        long pipelineStart = System.nanoTime();
        List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(
            (RedisCallback<Object>) connection -> {
                connection.zRangeByScore(
                    depositKey.getBytes(StandardCharsets.UTF_8), 
                    request.getBudgetMin(), 
                    request.getBudgetMax()
                );
                connection.zRangeByScore(
                    monthlyRentKey.getBytes(StandardCharsets.UTF_8), 
                    request.getMonthlyRentMin(), 
                    request.getMonthlyRentMax()
                );
                connection.zRangeByScore(
                    areaKey.getBytes(StandardCharsets.UTF_8), 
                    request.getAreaMin(), 
                    request.getAreaMax()
                );
                return null;
            }
        );
        long pipelineEnd = System.nanoTime();
        long pipelineLatency = pipelineEnd - pipelineStart;
        
        // 결과 추출
        @SuppressWarnings("unchecked")
        Set<byte[]> depositResult = (Set<byte[]>) pipelineResults.get(0);
        @SuppressWarnings("unchecked")
        Set<byte[]> rentResult = (Set<byte[]>) pipelineResults.get(1);
        @SuppressWarnings("unchecked")
        Set<byte[]> areaResult = (Set<byte[]>) pipelineResults.get(2);
        
        // 빈 결과 체크
        if (depositResult == null || depositResult.isEmpty() ||
            rentResult == null || rentResult.isEmpty() ||
            areaResult == null || areaResult.isEmpty()) {
            logPipelineMetrics(district, methodStart, pipelineLatency, "EMPTY");
            return Collections.emptyList();
        }
        
        // byte[] → String 변환 및 교집합
        Set<String> depositIds = depositResult.stream()
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .collect(Collectors.toSet());
        Set<String> rentIds = rentResult.stream()
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .collect(Collectors.toSet());
        Set<String> areaIds = areaResult.stream()
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
            .collect(Collectors.toSet());
        
        depositIds.retainAll(rentIds);
        depositIds.retainAll(areaIds);
        
        logPipelineMetrics(district, methodStart, pipelineLatency, "SUCCESS");
        return new ArrayList<>(depositIds);
        
    } catch (Exception e) {
        log.error("Pipeline 측정 중 오류 - 지역구: {}", district, e);
        return Collections.emptyList();
    }
}
```

#### 5.3.3 로깅 메소드

```java
/**
 * Before (순차 호출) 측정 결과 로깅
 */
private void logMetrics(String district, long methodStartNano, 
                        long totalCommandLatencyNano, int commandCount, String status) {
    long methodEndNano = System.nanoTime();
    double methodTimeMs = (methodEndNano - methodStartNano) / 1_000_000.0;
    double commandLatencyMs = totalCommandLatencyNano / 1_000_000.0;
    double ioRatio = (methodTimeMs > 0) ? (commandLatencyMs / methodTimeMs) * 100 : 0;
    
    log.info("[Metrics-Before] district={}, commands={}, methodTime={:.4f}ms, " +
             "commandLatency={:.4f}ms, ioRatio={:.2f}%, status={}",
        district, commandCount, methodTimeMs, commandLatencyMs, ioRatio, status);
}

/**
 * After (Pipeline) 측정 결과 로깅
 */
private void logPipelineMetrics(String district, long methodStartNano, 
                                 long pipelineLatencyNano, String status) {
    long methodEndNano = System.nanoTime();
    double methodTimeMs = (methodEndNano - methodStartNano) / 1_000_000.0;
    double pipelineLatencyMs = pipelineLatencyNano / 1_000_000.0;
    double ioRatio = (methodTimeMs > 0) ? (pipelineLatencyMs / methodTimeMs) * 100 : 0;
    
    log.info("[Metrics-After] district={}, commands=3(pipeline), methodTime={:.4f}ms, " +
             "commandLatency={:.4f}ms, ioRatio={:.2f}%, status={}",
        district, methodTimeMs, pipelineLatencyMs, ioRatio, status);
}
```

---

### 5.4 Layer 2: 네트워크 레벨 측정 (Wireshark)

#### 5.4.1 환경 설정

| 항목 | 설정값 |
|------|--------|
| 캡처 인터페이스 | Adapter for loopback traffic capture |
| 캡처 필터 | `port 6379` |
| 시간 형식 | 최초로 캡처된 패킷부터 지난 초 단위 시간 |
| 정밀도 | 나노초 |

#### 5.4.2 측정 절차

```
Step 1: Wireshark 캡처 시작
        - 인터페이스: loopback
        - 필터: port 6379

Step 2: 애플리케이션에서 추천 API 호출
        - POST /api/recommendations/monthly
        - 동일 조건으로 Before/After 각각 실행

Step 3: 캡처 중지 및 분석

Step 4: TCP Stream Follow로 명령-응답 쌍 식별

Step 5: 패킷 시간 차이 계산
```

#### 5.4.3 예상 패킷 패턴

**Before (순차 호출):**
```
No.   Time              Direction   Payload
1     0.000000000       →           ZRANGEBYSCORE idx:deposit:강남구 ...
2     0.001234567       ←           *N (응답)
3     0.001300000       →           ZRANGEBYSCORE idx:monthlyRent:강남구:월세 ...
4     0.002500000       ←           *N (응답)
5     0.002550000       →           ZRANGEBYSCORE idx:area:강남구:월세 ...
6     0.003700000       ←           *N (응답)

Network RTT #1 = 0.001234567 - 0.000000000 = 1.234567ms
Network RTT #2 = 0.002500000 - 0.001300000 = 1.200000ms
Network RTT #3 = 0.003700000 - 0.002550000 = 1.150000ms
총 Network RTT = 3.584567ms
패킷 수 = 6개
```

**After (Pipeline):**
```
No.   Time              Direction   Payload
1     0.000000000       →           ZRANGEBYSCORE... ZRANGEBYSCORE... ZRANGEBYSCORE...
2     0.001350000       ←           *N *N *N (3개 응답 연속)

Network RTT = 0.001350000 - 0.000000000 = 1.350000ms
패킷 수 = 2개
```

---

### 5.5 측정 지표 정의

| 지표 | 측정 계층 | 산출 방법 | 의미 |
|------|----------|----------|------|
| **메소드 총 시간** | Layer 1 | `methodEnd - methodStart` | 비즈니스 로직 포함 전체 시간 |
| **총 Command Latency** | Layer 1 | Before: 3개 명령 latency 합 / After: Pipeline latency | Redis I/O 총 소요 시간 |
| **I/O 비율** | Layer 1 | `(Command Latency / 메소드 시간) × 100` | I/O 바운드 정도 |
| **Network RTT** | Layer 2 | Wireshark 패킷 시간 차이 | 순수 네트워크 왕복 시간 |
| **패킷 수** | Layer 2 | Wireshark 패킷 카운트 | 네트워크 효율성 |

---

### 5.6 개선 효과 산출 공식

#### 5.6.1 애플리케이션 레벨 (Layer 1)

```
Command Latency 개선율 = (Before총CommandLatency - After총CommandLatency) 
                        / Before총CommandLatency × 100

예상값: ~66% (3회 → 1회)
```

#### 5.6.2 네트워크 레벨 (Layer 2)

```
Network RTT 개선율 = (Before총NetworkRTT - After총NetworkRTT) 
                    / Before총NetworkRTT × 100

패킷 감소율 = (Before패킷수 - After패킷수) / Before패킷수 × 100

예상값: RTT ~66%, 패킷 ~66% (6개 → 2개)
```

---

### 5.7 측정 시나리오

| 단계 | 작업 | 측정 도구 | 반복 횟수 |
|------|------|----------|----------|
| 1 | Before 코드 실행 | Java + Wireshark 동시 | 10회 |
| 2 | After 코드 실행 | Java + Wireshark 동시 | 10회 |
| 3 | 데이터 수집 | 로그 파일 + .pcap 파일 | - |
| 4 | 통계 산출 | 평균, 최소, 최대, 표준편차 | - |
| 5 | 교차 검증 | Layer 1 vs Layer 2 비교 | - |

---

### 5.8 보고서 수록 구조

```
4.1.4 성능 측정 결과

(1) 애플리케이션 레벨 측정 (Command Latency)
    ┌─────────────────────────────────────────────────────────┐
    │ 지표              │ Before (순차)  │ After (Pipeline) │
    ├───────────────────┼────────────────┼──────────────────┤
    │ 총 Command Latency│ X.XXXXms       │ X.XXXXms         │
    │ 메소드 총 시간    │ X.XXXXms       │ X.XXXXms         │
    │ I/O 비율          │ XX.XX%         │ XX.XX%           │
    │ 개선율            │      -         │ XX.XX%           │
    └─────────────────────────────────────────────────────────┘

(2) 네트워크 레벨 검증 (Wireshark 패킷 분석)
    ┌─────────────────────────────────────────────────────────┐
    │ 지표              │ Before (순차)  │ After (Pipeline) │
    ├───────────────────┼────────────────┼──────────────────┤
    │ 총 Network RTT    │ X.XXXXms       │ X.XXXXms         │
    │ 패킷 수           │ 6개            │ 2개              │
    │ RTT 개선율        │      -         │ XX.XX%           │
    │ 패킷 감소율       │      -         │ 66.67%           │
    └─────────────────────────────────────────────────────────┘

(3) 교차 검증 결론
    - Layer 1 (Command Latency)과 Layer 2 (Network RTT) 측정값의 
      개선율이 유사하게 나타남 → Pipeline 효과 입증
```

---

## 6. 2차 개선 계획: CompletableFuture 병렬화

> **주의**: 이 섹션은 2.3.1 병목 범위 외의 계획으로, 1차 개선 완료 후 진행한다.

### 6.1 대상 코드

```java
// MonthlyRecommendationService.java (103-109줄)
// performMonthlyStrictSearch 내부의 순차 루프
for (String district : filteredDistricts) {  // 최대 25회 순차 실행
    List<String> validProperties = findValidMonthlyPropertiesInDistrict(district, request);
    if (!validProperties.isEmpty()) {
        result.put(district, validProperties);
        totalFound += validProperties.size();
    }
}
```

### 6.2 문제점

- 25개 지역구를 **순차적으로** 처리
- 각 지역구 처리가 끝나야 다음 지역구 처리 시작
- 메인 스레드가 전체 시간 동안 블로킹

### 6.3 개선 방향

```java
// 병렬 처리를 위한 ExecutorService 구성
private final ExecutorService districtSearchExecutor = 
    Executors.newFixedThreadPool(5);  // 적정 병렬도 (조정 필요)

/**
 * 25개 지역구 병렬 검색 (CompletableFuture 적용)
 */
private Map<String, List<String>> performMonthlyStrictSearchParallel(
        MonthlyRecommendationRequestDto request,
        List<String> targetDistricts) {
    
    log.info("S-01: 월세 매물 병렬 검색 시작 - 대상: {}", targetDistricts.size());
    
    // 안전성 점수 필터링 (기존 로직 유지)
    List<String> filteredDistricts = filterByMinSafetyScore(targetDistricts, request);
    
    // 각 지역구별 비동기 작업 생성
    List<CompletableFuture<Map.Entry<String, List<String>>>> futures = 
        filteredDistricts.stream()
            .map(district -> CompletableFuture.supplyAsync(
                () -> {
                    // Pipeline 적용된 메소드 호출
                    List<String> properties = findValidMonthlyPropertiesInDistrictWithPipeline(district, request);
                    return Map.entry(district, properties);
                },
                districtSearchExecutor
            ))
            .toList();
    
    // 모든 작업 완료 대기 및 결과 집계
    Map<String, List<String>> result = futures.stream()
        .map(CompletableFuture::join)  // 블로킹 대기
        .filter(entry -> !entry.getValue().isEmpty())  // 빈 결과 제외
        .collect(Collectors.toMap(
            Map.Entry::getKey, 
            Map.Entry::getValue
        ));
    
    int totalFound = result.values().stream().mapToInt(List::size).sum();
    log.info("월세 매물 병렬 검색 완료: 총 {}개 매물 ID 발견 ({}개 지역구)", totalFound, result.size());
    
    return result;
}

/**
 * 리소스 정리 (애플리케이션 종료 시)
 */
@PreDestroy
public void shutdown() {
    districtSearchExecutor.shutdown();
    try {
        if (!districtSearchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            districtSearchExecutor.shutdownNow();
        }
    } catch (InterruptedException e) {
        districtSearchExecutor.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

### 6.4 예상 효과

```
Before (1차 개선 후, 순차):
25 districts × 1 RTT/district = 25 RTT (순차 실행)
예상 시간: 25 × (1 RTT + 처리시간)

After (2차 개선 후, 병렬):
ceil(25 districts / 5 workers) × 1 RTT = 5 라운드
예상 시간: 5 × (1 RTT + 처리시간)

개선율: 약 80% 시간 감소 (이상적 조건)
```

### 6.5 고려사항

| 항목 | 내용 | 해결 방안 |
|------|------|----------|
| **Redis 커넥션** | 동시 5개 스레드가 커넥션 요청 | Lettuce 단일 커넥션 멀티플렉싱 확인 (기본 지원) |
| **스레드 풀 크기** | CPU 코어 수, Redis 서버 부하 고려 | 부하 테스트 후 조정 (초기값 5) |
| **예외 처리** | 개별 지역구 실패 시 전체 영향 | `exceptionally()` 또는 `handle()`로 격리 |
| **타임아웃** | 특정 지역구 처리 지연 시 무한 대기 | `orTimeout(Duration)` 적용 |
| **결과 집계** | 스레드 안전성 | `CompletableFuture.join()` 후 순차 집계 |

### 6.6 예외 처리 강화 버전

```java
List<CompletableFuture<Map.Entry<String, List<String>>>> futures = 
    filteredDistricts.stream()
        .map(district -> CompletableFuture.supplyAsync(
            () -> Map.entry(district, findValidMonthlyPropertiesInDistrictWithPipeline(district, request)),
            districtSearchExecutor
        )
        .orTimeout(3, TimeUnit.SECONDS)  // 3초 타임아웃
        .exceptionally(ex -> {
            log.warn("지역구 {} 검색 실패: {}", district, ex.getMessage());
            return Map.entry(district, Collections.<String>emptyList());  // 실패 시 빈 결과
        }))
        .toList();
```

---

## 7. 차후 개선 가능성: Lua Script

> **참고**: 이 섹션은 추가 학습이 필요한 차후 개선 가능성으로, 현재 계획에는 포함되지 않는다.

### 7.1 Lua Script란

Lua는 Redis 서버 **내부에 임베딩된** 경량 스크립팅 언어다. Java 애플리케이션이 아니라 **Redis 서버 프로세스 안에서 실행**된다.

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Application (JVM)                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │   RedisTemplate.execute(luaScript, keys, args)      │    │
│  └──────────────────────┬──────────────────────────────┘    │
└─────────────────────────┼───────────────────────────────────┘
                          │ Network (1 RTT)
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Redis Server Process                      │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Lua VM (내장 인터프리터)                  │    │
│  │  ┌───────────────────────────────────────────────┐  │    │
│  │  │  redis.call('ZRANGEBYSCORE', key1, min, max)  │  │    │
│  │  │  redis.call('ZRANGEBYSCORE', key2, min, max)  │  │    │
│  │  │  -- 교집합 계산 (서버 측)                        │  │    │
│  │  │  return result                                 │  │    │
│  │  └───────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────┘    │
│                           ↓                                  │
│                    Redis Data Store                          │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Pipeline vs Lua Script 비교

| 구분 | Pipeline | Lua Script |
|------|----------|------------|
| **RTT** | 1회 (명령 묶음 전송) | 1회 (스크립트 1회 전송) |
| **중간 결과** | 모두 클라이언트로 반환 | 서버에서 처리, 최종 결과만 반환 |
| **교집합 연산 위치** | Java (클라이언트) | Lua (서버) |
| **네트워크 트래픽** | 3개 결과셋 전체 전송 | 교집합 결과만 전송 |
| **원자성** | 없음 (명령 사이에 다른 클라이언트 개입 가능) | 있음 (스크립트 전체가 원자적) |
| **복잡도** | 낮음 | 중간 (Lua 문법 학습 필요) |

### 7.3 Lua Script 예시 (참고용)

```lua
-- 3개 ZSet 조회 + 교집합을 1 RTT로 처리
local deposit = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2])
local rent = redis.call('ZRANGEBYSCORE', KEYS[2], ARGV[3], ARGV[4])
local area = redis.call('ZRANGEBYSCORE', KEYS[3], ARGV[5], ARGV[6])

-- Lua 테이블로 교집합 계산
local depositSet = {}
for _, v in ipairs(deposit) do depositSet[v] = true end

local rentSet = {}
for _, v in ipairs(rent) do
    if depositSet[v] then
        rentSet[v] = true
    end
end

local result = {}
for _, v in ipairs(area) do
    if rentSet[v] then
        table.insert(result, v)
    end
end

return result
```

### 7.4 적용 시 추가 이점

- 3개 결과셋 전체가 아닌 **교집합 결과만** 네트워크 전송
- Redis 서버 측에서 교집합 계산 (Java CPU 부하 감소)
- 원자적 실행 (경쟁 조건 없음)

---

## 8. 보고서 구조 (포트폴리오 수록용)

### 8.1 병목 현상 문서 4절 구조

```
4. 개선 사례

4.1 Redis Pipeline 적용을 통한 RTT 최적화

4.1.1 개선 배경
- 대상 위치: MonthlyRecommendationService.findValidMonthlyPropertiesInDistrict()
- 문제: 단일 지역구당 3회의 순차적 Redis 조회로 RTT 누적
- 영향 범위: 25개 지역구 순회 시 최대 75회 RTT 발생

4.1.2 기술적 분석
- 기존 구조의 네트워크 I/O 패턴 분석
- 동기 블로킹 호출의 스레드 자원 점유 문제
- Redis Pipeline 프로토콜의 동작 원리
- Early Termination 트레이드오프 분석

4.1.3 개선 구현
- executePipelined() 적용 코드 (Before/After 비교)
- 결과 타입 변환 처리 (byte[] → String)
- 기존 비즈니스 로직 유지 (교집합 계산)

4.1.4 성능 측정 결과
- 측정 환경 및 조건
- Layer 1 (애플리케이션 레벨) Before/After 비교 데이터
  - 메소드 실행 시간
  - 총 Command Latency
  - I/O 비율
- Layer 2 (네트워크 레벨) Before/After 비교 데이터
  - 총 Network RTT
  - 패킷 수
- 개선율 산출 및 교차 검증

4.1.5 한계 및 추가 개선 가능성
- 현재 개선의 범위: 단일 지역구 내 RTT 최적화
- 2차 개선 계획: 지역구 간 병렬 처리 (CompletableFuture)
  - 대상: performMonthlyStrictSearch() 내 순차 루프
  - 예상 효과: 25회 순차 → 5회 병렬 (약 80% 개선)
  - 고려사항: 스레드 풀 크기, 예외 처리, 타임아웃
- 차후 가능성: Lua Script를 통한 서버 측 교집합 연산
  - 추가 이점: 네트워크 트래픽 감소, 원자적 실행
```

---

## 9. 작업 순서

| 단계 | 작업 | 산출물 | 비고 |
|------|------|--------|------|
| **1** | 현재 코드 성능 측정 (Before) - Layer 1 | 로그 파일 (Command Latency) | 측정 코드 삽입 |
| **2** | 현재 코드 성능 측정 (Before) - Layer 2 | .pcap 파일 (Network RTT) | Wireshark 캡처 |
| **3** | Pipeline 적용 코드 구현 | 수정된 Service 코드 | 4.3절 참조 |
| **4** | 개선 후 성능 측정 (After) - Layer 1 | 로그 파일 (Command Latency) | 동일 조건 측정 |
| **5** | 개선 후 성능 측정 (After) - Layer 2 | .pcap 파일 (Network RTT) | Wireshark 캡처 |
| **6** | Before/After 비교 분석 | 분석 보고서 | Layer 1, 2 교차 검증 |
| **7** | 보고서 작성 (4.1절) | 병목 현상 문서 업데이트 | 8.1절 구조 |
| **8** | 2차 개선 계획 문서화 | 보고서 4.1.5절에 포함 | 계획만 |

---

## 10. 관련 파일 목록

| 파일 | 역할 | 수정 여부 |
|------|------|----------|
| `MonthlyRecommendationService.java` | 월세 추천 서비스 (병목 대상) | 수정 대상 |
| `CharterRecommendationService.java` | 전세 추천 서비스 (동일 구조) | 동일 패턴 적용 가능 |
| `RedisHandler.java` | Redis 유틸리티 | 선택적 확장 |
| `RedisSingleDataService.java` | Redis 단일 데이터 서비스 | 참조 |
| `RedisSingleDataController.java` | Redis API 컨트롤러 | 참조 |
| `RedisDto.java` | Redis DTO | 참조 |

---

## 11. 핵심 코드 참조

### 11.1 현재 코드 (수정 대상)

**파일**: `MonthlyRecommendationService.java`
**위치**: 206-241줄
**메소드**: `findValidMonthlyPropertiesInDistrict()`

### 11.2 호출 컨텍스트

**파일**: `MonthlyRecommendationService.java`
**위치**: 86-114줄
**메소드**: `performMonthlyStrictSearch()`
**루프 위치**: 103-109줄

---

## 12. 용어 정의

| 용어 | 정의 |
|------|------|
| **Command Latency** | Redis 명령 메소드(`rangeByScore()` 등) 호출 시작부터 반환까지의 총 소요 시간. 직렬화, syscall, 네트워크, Redis 처리, 역직렬화 비용을 모두 포함 |
| **총 Command Latency** | 여러 Redis 명령의 Command Latency 합계. Before: 3개 명령의 개별 latency 합 / After: Pipeline 1회 latency |
| **I/O 비율** | (총 Command Latency / 메소드 총 시간) × 100. I/O 바운드 정도를 나타내며, 80% 이상이면 Pipeline 개선 효과가 큼 |
| **Network RTT** | 패킷이 네트워크를 왕복하는 순수 시간. Wireshark 등 패킷 캡처 도구로만 측정 가능 |
| **RTT (Round Trip Time)** | 클라이언트에서 서버로 요청을 보내고 응답을 받기까지의 왕복 시간 (일반적 정의) |
| **Pipeline** | 여러 Redis 명령을 하나의 네트워크 패킷으로 묶어 전송하는 기법 |
| **동기 블로킹** | 호출 스레드가 응답을 받을 때까지 대기하며 다른 작업을 수행하지 못하는 상태 |
| **Early Termination** | 중간 결과가 빈 경우 이후 작업을 생략하고 즉시 반환하는 최적화 기법 |
| **교집합 (Intersection)** | 여러 집합에 공통으로 포함된 원소들의 집합 |
| **Lua Script** | Redis 서버 내장 Lua 인터프리터에서 실행되는 스크립트 |
| **CompletableFuture** | Java의 비동기 프로그래밍을 위한 Future 확장 클래스 |
| **Layer 1 측정** | 애플리케이션 레벨에서 `System.nanoTime()`을 사용한 Command Latency 측정 |
| **Layer 2 측정** | 네트워크 레벨에서 Wireshark를 사용한 Network RTT 및 패킷 수 측정 |

---

## 문서 끝
