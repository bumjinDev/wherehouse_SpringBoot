# Origin N+1 패턴 성능 분석 보고서
## 1차 테스트 결과 분석 - 병목 지점 식별 및 기준선 수립

---

**문서 정보**
- 보고서 제목: Origin Code(N+1 패턴) 성능 분석 - 병목 지점 기준선 수립
- 시험 일자: 2025년 1월 8일
- 시험 버전: Origin Code (Bottleneck Point 2.1.1)
- 작성자: 정범진
- 문서 버전: 1.0

---

## 요약 (Executive Summary)

본 보고서는 전세 매물 추천 서비스(`CharterRecommendationService`)의 `calculateCharterPropertyScores()` 메서드에서 발생하는 N+1 쿼리 패턴의 실제 성능을 측정하고, 후속 최적화를 위한 기준선(Baseline)을 수립한 결과를 제시한다.

**핵심 결론: N+1 패턴의 실제 병목은 RDB 조회가 아닌 Redis 조회 및 점수 계산 로직에 있다.**

### 주요 측정 결과

| 지표 | 측정값 | 비고 |
|------|--------|------|
| **평균 총 소요 시간** | 5,531ms | calculateCharterPropertyScores() 전체 |
| **평균 RDB 조회 시간** | 343ms | 25회 쿼리의 누적 합계 |
| **RDB 시간 비율** | 6.2% | 전체 대비 RDB 점유율 |
| **비-RDB 처리 시간** | 5,188ms (93.8%) | Redis + 점수 계산 + 기타 |
| **RDB 호출 횟수** | 25회 | 서울시 25개 자치구 |
| **처리 매물 수** | 8,660건 | 전체 조회 대상 |
---

## 1. 테스트 환경 및 조건

### 1.1 시스템 구성

| 구분 | 사양 |
|------|------|
| **데이터베이스** | Oracle 11g XE |
| **캐시** | Redis (매물 상세 정보 저장) |
| **애플리케이션** | Spring Boot 3.2.0 |
| **ORM** | Hibernate + JPA |
| **커넥션 풀** | HikariCP (max-pool-size: 10, timeout: 2s) |
| **부하 생성** | JMeter 50 Threads (동시 접속) |
| **테스트 데이터** | 8,660개 매물, 25개 자치구 |

### 1.2 테스트 대상 코드 (Origin Code)

```java
/**
 * S-04: 전세 매물 점수 계산 (Origin N+1 패턴)
 * 병목 지점: 자치구별 루프 내에서 RDB 조회 반복
 */
private Map<String, List<PropertyWithScore>> calculateCharterPropertyScores(
        Map<String, List<String>> districtProperties, 
        CharterRecommendationRequestDto request) {

    for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {
        String districtName = entry.getKey();
        List<String> propertyIds = entry.getValue();

        // 1. Redis에서 매물 상세 정보 조회
        List<PropertyDetail> propertyDetails = 
            getMultipleCharterPropertiesFromRedis(propertyIds);

        // 2. [N+1 문제] RDB에서 리뷰 통계 조회 (25회 반복)
        Map<String, ReviewStatistics> reviewStatsMap = 
            getReviewStatisticsFromRDB(propertyIds);

        // 3. 점수 계산 로직
        for (PropertyDetail propertyDetail : propertyDetails) {
            // 정량 점수 + 정성 점수 계산...
        }
    }
}
```

### 1.3 측정 방법

코드 내 `System.currentTimeMillis()`를 활용한 구간별 시간 측정:

```java
long totalLoopStart = System.currentTimeMillis();
long totalRdbTime = 0;
int rdbCallCount = 0;

for (...) {
    long rdbStart = System.currentTimeMillis();
    Map<String, ReviewStatistics> reviewStatsMap = getReviewStatisticsFromRDB(propertyIds);
    long rdbDuration = System.currentTimeMillis() - rdbStart;
    totalRdbTime += rdbDuration;
    rdbCallCount++;
}

long totalDuration = System.currentTimeMillis() - totalLoopStart;
```

---

## 2. 측정 결과

### 2.1 기술 통계량

#### 총 소요 시간 (Total Execution Time)

| 통계량 | 값 |
|--------|-----|
| 평균 | 5,531ms |
| 중앙값 | 5,545ms |
| 표준편차 | 56ms |
| 최소값 | 5,398ms |
| 최대값 | 5,602ms |
| 범위 | 204ms |

#### RDB 조회 시간 (누적)

| 통계량 | 값 |
|--------|-----|
| 평균 | 343ms |
| 중앙값 | 337ms |
| 표준편차 | 50ms |
| 최소값 | 230ms |
| 최대값 | 437ms |
| 범위 | 207ms |

#### RDB 시간 비율

| 통계량 | 값 |
|--------|-----|
| 평균 | 6.2% |
| 중앙값 | 6.1% |
| 표준편차 | 0.9% |
| 최소값 | 4.1% |
| 최대값 | 8.0% |

### 2.2 시간 분해 분석

```
총 소요 시간: 5,531ms (100%)
│
├─ RDB 조회 시간: 343ms (6.2%)
│  └─ 25회 × 평균 13.7ms/회
│
└─ 비-RDB 처리 시간: 5,188ms (93.8%)
   ├─ Redis 조회 (getMultipleCharterPropertiesFromRedis)
   ├─ 점수 계산 로직 (calculateHybridScore 등)
   ├─ Map 변환 및 정렬
   └─ 기타 오버헤드
```

### 2.3 스레드별 분포 분석

50개 스레드의 성능 분포를 분석한 결과, 매우 균일한 성능을 보였다.

#### 총 소요 시간 분포

| 구간 | 건수 | 비율 |
|------|------|------|
| 5,400ms 미만 | 2건 | 4% |
| 5,400ms ~ 5,500ms | 12건 | 24% |
| 5,500ms ~ 5,550ms | 16건 | 32% |
| 5,550ms ~ 5,600ms | 18건 | 36% |
| 5,600ms 이상 | 2건 | 4% |

#### RDB 조회 시간 분포

| 구간 | 건수 | 비율 |
|------|------|------|
| 300ms 미만 | 12건 | 24% |
| 300ms ~ 350ms | 18건 | 36% |
| 350ms ~ 400ms | 15건 | 30% |
| 400ms 이상 | 5건 | 10% |

### 2.4 성공률 및 안정성

| 지표 | 결과 |
|------|------|
| 총 요청 수 | 50개 |
| 성공 건수 | 50개 (100%) |
| 실패 건수 | 0개 |
| Connection Timeout | 미발생 |
| ORA-01795 에러 | 해당 없음 |
| 최종 상태 | 전원 SUCCESS_NORMAL |

---

## 3. 성능 분석

### 3.1 N+1 패턴의 실제 비용 분석

#### 예상 시나리오 vs 실제 결과

| 항목 | 예상 | 실제 |
|------|------|------|
| RDB 비중 | 50% 이상 | **6.2%** |
| 주요 병목 | RDB N+1 호출 | **비-RDB 처리** |
| 회당 RDB 시간 | 50ms+ | **13.7ms** |


### 3.3 동시성 처리 분석

50개 스레드가 동시에 요청했음에도 모든 요청이 성공한 것은:

1. **HikariCP 풀 활용**: 10개 커넥션으로 50개 요청을 순차 처리
2. **짧은 커넥션 점유 시간**: RDB당 13.7ms로 빠른 커넥션 반환
3. **대기 시간 허용**: 2초 타임아웃 내에 모든 요청 처리 완료

#### 커넥션 회전 분석

```
커넥션 풀: 10개
동시 요청: 50개
RDB 호출당 점유 시간: ~14ms
요청당 총 RDB 점유 시간: 14ms × 25회 = 350ms

이론적 처리 용량: 10 커넥션 × (1000ms / 350ms) ≒ 28.5 요청/초
실제 처리: 50 요청 / 5.5초 ≒ 9.1 요청/초
```

실제 처리량이 이론치보다 낮은 이유는 비-RDB 처리(5,188ms)가 전체 처리 시간을 지배하기 때문이다.

---

## 4. RDB 조회 패턴 상세 분석

### 4.1 자치구별 쿼리 특성

서울시 25개 자치구에 대해 각각 1회씩 RDB 조회가 수행되었다.

```sql
-- 각 자치구별 쿼리 형태 (예시)
SELECT rs.PROPERTY_ID, rs.AVG_RATING, rs.REVIEW_COUNT, 
       rs.POSITIVE_KEYWORD_COUNT, rs.NEGATIVE_KEYWORD_COUNT
FROM REVIEW_STATISTICS rs
WHERE rs.PROPERTY_ID IN (?, ?, ?, ... ?)  -- 평균 346개 파라미터
```

### 4.2 쿼리당 평균 처리 시간

```
총 RDB 시간: 343ms
쿼리 횟수: 25회
─────────────────
쿼리당 평균: 13.7ms
```

---

## 5. 다음 테스트 사항

#### 2차 테스트: Bulk Fetch 방식

**목적**: N+1 제거의 실제 효과 및 대량 IN 절의 위험성 검증

**예상 결과**:
- ORA-01795 에러 발생 가능성 (8,660개 > 1,000개 제한)
- Hibernate 내부 분할 시 Hard Parse 부하 증가

#### 3차 테스트: Chunk 방식 (1,000개 단위)

**목적**: 안정성과 성능의 균형점 확보

**예상 결과**:
- RDB 호출: 9회 (8,660 ÷ 1,000)
- Soft Parse 유지로 안정적 성능
- Origin 대비 RDB 시간 50% 감소 예상

---

## 6. 부록

### A. 스레드별 상세 측정 데이터 (샘플)

| Thread ID | 총 시간(ms) | RDB 시간(ms) | RDB 비율 | 상태 |
|-----------|-------------|--------------|----------|------|
| exec-46 | 5,398 | 327 | 6.1% | SUCCESS_NORMAL |
| exec-8 | 5,409 | 364 | 6.7% | SUCCESS_NORMAL |
| exec-19 | 5,409 | 336 | 6.2% | SUCCESS_NORMAL |
| exec-25 | 5,408 | 282 | 5.2% | SUCCESS_NORMAL |
| exec-12 | 5,406 | 434 | 8.0% | SUCCESS_NORMAL |
| ... | ... | ... | ... | ... |
| exec-10 | 5,602 | 353 | 6.3% | SUCCESS_NORMAL |

**전체 데이터**: `1차_테스트_스레드별_이벤트_분석결과.csv` 참조

### B. 로그 출력 샘플

```
15:01:11.982 [http-nio-8185-exec-46] WARN  CharterRecommendationService 
  - === [Bottleneck Point: 2.1.1 (Origin N+1)] ===
15:01:11.982 [http-nio-8185-exec-46] WARN  CharterRecommendationService 
  - 1. 총 소요 시간: 5398ms
15:01:11.983 [http-nio-8185-exec-46] WARN  CharterRecommendationService 
  - 2. RDB 조회 시간 (Sum): 327ms (전체의 6.1%)
15:01:11.983 [http-nio-8185-exec-46] WARN  CharterRecommendationService 
  - 3. RDB 호출 횟수: 25회 (자치구 수만큼 반복)
15:01:11.983 [http-nio-8185-exec-46] WARN  CharterRecommendationService 
  - 4. 총 매물 수: 8660건
15:01:11.983 [http-nio-8185-exec-46] WARN  CharterRecommendationService 
  - =============================================
15:01:11.983 [http-nio-8185-exec-46] INFO  CharterRecommendationService 
  - S-05: 지역구 점수 계산 및 정렬 시작
```
---

**보고서 끝**
