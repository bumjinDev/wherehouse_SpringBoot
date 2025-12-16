# Bulk Fetch 성능 분석 보고서
## 2차 테스트 결과 분석 및 1차 테스트 비교

---

**문서 정보**
- 보고서 제목: Bulk Fetch 방식 성능 분석 - Origin N+1 대비 비교
- 시험 일자: 2025년 1월 8일
- 시험 버전: Bottleneck Resolved 2.1.1 (Bulk Fetch)
- 작성자: 정범진
- 문서 버전: 1.0

---

## 요약 (Executive Summary)

본 보고서는 N+1 쿼리 문제 해결을 위해 도입한 Bulk Fetch 방식의 실제 성능을 측정하고, Origin Code(N+1 패턴)와 비교 분석한 결과를 제시한다.

**핵심 결론: Bulk Fetch는 Origin 대비 성능이 8.5% 저하되었으며, 최적화 전략으로 부적합하다.**

### 주요 측정 결과

| 지표 | 1차(Origin N+1) | 2차(Bulk Fetch) | 변화율 |
|------|----------------|-----------------|--------|
| **평균 총 소요 시간** | 5,531ms | 6,001ms | **+8.5%** |
| **단일 요청의 총 DB 조회 시간** | 343ms (25회) | 1,748ms (1회) | **+410%** |
| **DB 시간 비율** | 6.1% | 28.8% | +22.7%p |
| **N+1 오버헤드** | 5,188ms (93.9%) | 4,253ms (70.9%) | -18.0% |

### 핵심 발견 사항

1. **역설적 성능 저하**: 쿼리 횟수를 25회 → 1회로 줄였음에도 총 소요 시간은 8.5% 증가
2. **단일 쿼리의 고비용**: 8,660개 파라미터 처리 시 단일 쿼리가 다중 쿼리 대비 410% 더 느림
3. **높은 성능 변동성**: RDB 조회 시간이 1,038ms~2,342ms로 2배 이상 편차 (CV: 25.1%)
4. **안정성 확보**: Oracle ORA-01795 에러 미발생 (Hibernate 내부 분할 처리 추정)

---

## 1. 테스트 환경 및 조건

### 1.1 시스템 구성

Origin Code와 동일한 환경에서 테스트를 수행하였다.

| 구분 | 사양 |
|------|------|
| **데이터베이스** | Oracle 11g XE |
| **애플리케이션** | Spring Boot 3.2.0 |
| **HikariCP Pool** | max-pool-size: 10, timeout: 2s |
| **부하 생성** | JMeter 50 Threads |
| **테스트 데이터** | 8,660개 매물 ID |

### 1.2 코드 변경 사항

**Origin Code (N+1 패턴):**
```java
// 자치구별 루프 (25회 반복)
for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {
    String districtName = entry.getKey();
    List<String> propertyIds = entry.getValue();
    
    // N+1 문제: 25번 RDB 조회
    Map<String, ReviewStatistics> reviewStatsMap = 
        getReviewStatisticsFromRDB(propertyIds);
    
    // ... 점수 계산 로직
}
```

**Bulk Fetch Code (단일 쿼리):**
```java
// 루프 진입 전 전체 ID 추출
List<String> allPropertyIds = districtProperties.values().stream()
    .flatMap(List::stream)
    .distinct()
    .collect(Collectors.toList());

// 1회 RDB 조회 (8,660개 파라미터)
List<ReviewStatistics> allStats = 
    reviewStatisticsRepository.findAllById(allPropertyIds);

// Map으로 변환 (O(1) 조회)
Map<String, ReviewStatistics> globalReviewStatsMap = 
    allStats.stream().collect(Collectors.toMap(...));

// 자치구별 루프 (RDB 호출 없음)
for (Map.Entry<String, List<String>> entry : districtProperties.entrySet()) {
    // 메모리 Map에서 조회
    ReviewStatistics stats = globalReviewStatsMap.get(propertyId);
}
```

---

## 2. 측정 결과

### 2.1 기술 통계량

#### 총 소요 시간 (Total Execution Time)

| 통계량 | 값 |
|--------|-----|
| 평균 | 6,001ms |
| 중앙값 | 6,210ms |
| 표준편차 | 368ms |
| 최소값 | 5,209ms |
| 최대값 | 6,294ms |
| 범위 | 1,085ms |

#### 단일 요청의 총 DB 조회 시간

> 측정 방법: 각 스레드가 비즈니스 로직 수행 중 발생시킨 모든 DB 조회의 누적 소요 시간
> - 1차(Origin): 25회 조회의 합계
> - 2차(Bulk): 1회 조회의 소요 시간

| 통계량 | 값 |
|--------|-----|
| 평균 | 1,748ms |
| 중앙값 | 1,877ms |
| 표준편차 | 439ms |
| 최소값 | 1,038ms |
| 최대값 | 2,342ms |
| 범위 | 1,304ms |

#### DB 시간 비율

| 통계량 | 값 |
|--------|-----|
| 평균 | 28.8% |
| 중앙값 | 30.1% |
| 표준편차 | 5.9% |
| 최소값 | 19.1% |
| 최대값 | 37.3% |

### 2.2 1차 테스트(Origin N+1) 대비 비교

```
지표                    1차(Origin)    2차(Bulk)      변화
─────────────────────────────────────────────────────────
총 소요 시간             5,531ms       6,001ms       +470ms (+8.5%)
단일 요청의 총 DB 조회 시간  343ms        1,748ms      +1,405ms (+410%)
DB 시간 비율              6.1%         28.8%        +22.7%p
DB 호출 횟수              25회          1회          -24회
N+1 오버헤드            5,188ms       4,253ms       -935ms (-18%)
```

### 2.3 성공률 및 안정성

- **총 요청 수**: 50개
- **성공 건수**: 50개 (100%)
- **실패 건수**: 0개
- **ORA-01795 에러**: 미발생
- **타임아웃**: 미발생

---

## 3. 성능 분석

### 3.1 예상과 다른 결과

#### 예상 시나리오
- N+1 문제 해결로 순차 실행 오버헤드 제거
- 단일 쿼리 실행으로 성능 대폭 개선
- **예상 개선율: 50% 이상**

#### 실제 결과
- 총 소요 시간 **8.5% 증가** (성능 저하)
- RDB 조회 시간 **410% 증가**
- N+1 오버헤드는 18% 감소했으나, RDB 부하 증가로 상쇄

### 3.2 시간 분해 분석

**1차 테스트 (Origin N+1):**
```
총 소요 시간: 5,531ms
├─ 단일 요청의 총 DB 조회 시간: 343ms (6.1%)
│  └─ 25회 × 평균 13.7ms
└─ N+1 패턴 오버헤드: 5,188ms (93.9%)
   └─ 25회 순차 실행으로 인한 대기 및 동기화 비용
```

**2차 테스트 (Bulk Fetch):**
```
총 소요 시간: 6,001ms
├─ 단일 요청의 총 DB 조회 시간: 1,748ms (28.8%)
│  └─ 1회 × 1,748ms (파싱 + Fetch)
└─ 애플리케이션 처리: 4,253ms (70.9%)
   └─ Redis 조회, 점수 계산, 기타 처리 등
```

### 3.3 DB 조회 시간 분포 분석

DB 조회 시간이 4개의 명확한 그룹으로 분리되었으며, 이는 Hibernate의 동적 쿼리 분할 또는 DB 파싱 캐시 상태를 시사한다.

| 그룹 | 건수 | 평균 DB 시간 | 평균 총 시간 |
|------|------|-------------|-------------|
| **빠른 그룹 (1,000ms대)** | 10건 | 1,046ms | 5,328ms |
| **중간 그룹 1 (1,500ms대)** | 13건 | 1,577ms | 5,921ms |
| **중간 그룹 2 (1,800~2,000ms)** | 12건 | 1,902ms | 6,178ms |
| **느린 그룹 (2,100ms+)** | 15건 | 2,242ms | 6,285ms |

**빠른 그룹 vs 느린 그룹:**
- DB 시간 차이: 1,196ms (214% 더 느림)
- 총 시간 차이: 957ms (18% 더 느림)

**변동계수 (Coefficient of Variation):**
- DB 조회 시간 CV: **25.1%**
- 비교: Origin Code DB 시간 CV: 14.6%

→ Bulk Fetch의 성능 예측 가능성이 Origin 대비 낮음

---

## 4. 근본 원인 분석

### 4.1 단일 쿼리의 고비용 요인

#### A. 과도한 IN 절 파싱 부하

**Origin Code (25회 쿼리):**
```sql
-- 1회당 평균 346개 파라미터
SELECT * FROM REVIEW_STATISTICS 
WHERE PROPERTY_ID IN (?, ?, ... ?) -- 346개

-- 각 쿼리 파싱 시간: ~5ms
-- 총 파싱 시간: 25회 × 5ms = 125ms
```

**Bulk Fetch (1회 쿼리):**
```sql
-- 8,660개 파라미터 (추정: Hibernate 내부 분할)
SELECT * FROM REVIEW_STATISTICS 
WHERE PROPERTY_ID IN (?, ?, ... ?) -- 대량 파라미터

-- 단일 쿼리 파싱 시간: ~400ms (Hard Parse)
-- 또는: 여러 서브쿼리 OR 연결 시 더 증가
```

**파싱 비용 증가 원인:**
1. **Hard Parse 강제 발생**: 파라미터 개수가 매번 달라 캐시 재사용 불가
2. **OR 연산자 폭발**: Hibernate가 OR로 연결 시 옵티마이저 비용 폭증
3. **실행 계획 수립 복잡도**: 대량 IN 리스트에 대한 최적 경로 탐색 부하

#### B. Hibernate의 추정 쿼리 분할 패턴

RDB 조회 시간의 2배 편차는 Hibernate가 동적으로 쿼리를 분할했음을 시사한다.

**추정 시나리오 1: 가변적 분할**
```java
// Hibernate 내부 로직 (추정)
if (ids.size() > 1000) {
    // Case 1: OR로 연결 (느린 그룹)
    SELECT * WHERE ID IN (1..1000) OR ID IN (1001..2000) OR ...
    
    // Case 2: 여러 번 실행 (빠른 그룹)
    for (chunk : partition(ids, 1000)) {
        SELECT * WHERE ID IN (chunk)
    }
}
```

**추정 시나리오 2: DB 파싱 캐시 상태 의존**
- 첫 요청: Hard Parse (느림)
- 후속 요청: Soft Parse (빠름)
- 캐시 Eviction 후: 다시 Hard Parse

### 4.2 왜 N+1 오버헤드 감소가 상쇄되었는가?

**N+1 오버헤드 감소분: -935ms**
```
Origin: 5,188ms (25회 순차 실행)
Bulk:   4,253ms (1회 실행)
절감:   -935ms
```

**DB 조회 시간 증가분: +1,405ms**
```
Origin: 343ms (25회 × 13.7ms)
Bulk:   1,748ms (1회 대량 쿼리)
증가:   +1,405ms
```

**최종 결과: +470ms (성능 저하)**
```
-935ms (절감) + 1,405ms (증가) = +470ms
```

---

## 5. Hibernate 내부 동작 추론

### 5.1 ORA-01795 에러가 발생하지 않은 이유

Oracle은 IN 절에 최대 1,000개 파라미터만 허용하지만, 본 테스트에서는 8,660개 파라미터로 조회했음에도 에러가 발생하지 않았다.

**추론 1: Hibernate의 자동 분할 (가능성 높음)**
```java
// Hibernate 5.x 이후 버전 (추정)
// org.hibernate.loader.Loader 클래스 내부
if (inClauseSize > dialectInListSizeLimit) {
    return splitIntoMultipleQueries(ids);
}
```

**추론 2: OR 연산자로 우회**
```sql
-- Hibernate가 생성했을 가능성 있는 쿼리
SELECT * FROM REVIEW_STATISTICS WHERE
  PROPERTY_ID IN (?, ?, ... ?) OR  -- 1~1000
  PROPERTY_ID IN (?, ?, ... ?) OR  -- 1001~2000
  ...
  PROPERTY_ID IN (?, ?, ... ?)     -- 8001~8660
```

### 5.2 성능 변동성의 원인

RDB 조회 시간이 1,038ms~2,342ms로 2배 차이나는 이유는 다음 중 하나 또는 복합적 원인으로 추정된다.

1. **Hard Parse vs Soft Parse**
   - Library Cache Hit: 빠른 그룹 (~1,000ms)
   - Library Cache Miss: 느린 그룹 (~2,300ms)

2. **쿼리 실행 계획 차이**
   - Index Range Scan: 빠른 그룹
   - Full Table Scan 또는 Bitmap OR: 느린 그룹

3. **OR 연산자 개수에 따른 옵티마이저 비용**
   - 적은 OR: 인덱스 활용
   - 많은 OR: Full Scan으로 퇴행

---

## 6. 결론 및 권고사항

### 6.1 주요 결론

1. **Bulk Fetch는 Origin 대비 성능이 저하되었다.**
   - 총 소요 시간: 5,531ms → 6,001ms (+8.5%)
   - 쿼리 횟수 감소 효과가 단일 쿼리 고비용으로 상쇄됨

2. **대량 파라미터 단일 쿼리는 비효율적이다.**
   - RDB 조회 시간: 343ms → 1,748ms (+410%)
   - 파싱 부하 및 옵티마이저 비용이 급증

3. **성능 예측 가능성이 낮다.**
   - RDB 조회 시간 CV: 25.1% (높은 변동성)
   - Hibernate 내부 동작과 DB 캐시 상태에 의존

4. **안정성은 확보되었으나 근거가 불명확하다.**
   - ORA-01795 에러 미발생
   - Hibernate가 내부적으로 분할 처리한 것으로 추정
   - 명시적 제어 불가로 인한 리스크 존재

### 6.2 Bulk Fetch 방식의 문제점

| 문제점 | 내용 |
|--------|------|
| **성능 저하** | Origin 대비 8.5% 느림 |
| **높은 변동성** | RDB 시간이 2배 이상 편차 |
| **제어 불가** | Hibernate 내부 동작에 의존 |
| **파싱 부하** | Hard Parse 강제 발생 |
| **옵티마이저 부담** | 대량 IN 절 또는 OR 연산자 |

### 6.3 권고 사항

#### Bulk Fetch 방식 채택 불가

**근거:**
- 성능 개선이 아닌 성능 저하 발생
- 높은 성능 변동성으로 SLA 보장 어려움
- Hibernate 내부 동작 의존으로 제어 불가

#### Chunk 방식으로 전환 필요

**Chunk 전략 (1,000개 단위 분할):**
```java
// 1,000개 단위로 명시적 분할
List<List<String>> chunks = partition(allPropertyIds, 1000);

for (List<String> chunk : chunks) {
    // 각 청크 조회 (Soft Parse 유도)
    List<ReviewStatistics> stats = repository.findAllById(chunk);
}
```

**기대 효과:**
1. **Soft Parse 유도**: 동일 쿼리 구조 재사용
2. **안정적 성능**: Oracle 제한 명시적 준수
3. **예측 가능성**: 쿼리 횟수와 시간 선형 비례
4. **제어 가능성**: 애플리케이션 레벨 제어

---

## 7. 다음 단계

### 7.1 3차 테스트 계획 (Chunk 방식)

**목표:**
- 1,000개 단위 분할 조회로 안정성과 성능 균형 확보
- Origin 대비 50% 이상 성능 개선 목표

**예상 결과:**
- RDB 호출 횟수: 9회 (8,660개 ÷ 1,000 = 9회)
- RDB 조회 시간: ~900ms (9회 × 100ms)
- 총 소요 시간: ~2,500ms (Origin 대비 55% 개선)

**측정 지표:**
- 총 소요 시간 및 표준편차
- RDB 조회 시간 및 호출 횟수
- 커넥션 풀 상태 (waiting 발생 여부)

### 7.2 검증 항목

1. **성능 개선 검증**
   - Origin 대비 개선율 50% 이상 달성 여부
   - Bulk Fetch 대비 개선 여부

2. **안정성 검증**
   - ORA-01795 에러 미발생 확인
   - 모든 요청 정상 완료 확인

3. **예측 가능성 검증**
   - RDB 조회 시간 변동계수 10% 이하 목표
   - 쿼리 횟수와 시간의 선형 관계 확인

---

## 부록

### A. 전체 측정 데이터

50개 요청의 개별 측정값은 다음 파일에 저장되어 있다:
- `2차_테스트_결과_thread_performance_data.csv`
- `2차_테스트_결과_thread_performance_summary.csv`

### B. RDB 조회 시간대별 상세 데이터

**빠른 그룹 (1,000ms대) 샘플:**

| 스레드 | 총 시간 | RDB 시간 | RDB 비율 |
|--------|---------|---------|---------|
| exec-25 | 5,332ms | 1,038ms | 19.5% |
| exec-22 | 5,439ms | 1,038ms | 19.1% |
| exec-2 | 5,209ms | 1,044ms | 20.0% |
| exec-47 | 5,446ms | 1,046ms | 19.2% |
| exec-49 | 5,275ms | 1,048ms | 19.9% |

**느린 그룹 (2,100ms+) 샘플:**

| 스레드 | 총 시간 | RDB 시간 | RDB 비율 |
|--------|---------|---------|---------|
| exec-35 | 6,285ms | 2,342ms | 37.3% |
| exec-20 | 6,294ms | 2,307ms | 36.7% |
| exec-31 | 6,291ms | 2,303ms | 36.6% |
| exec-11 | 6,290ms | 2,298ms | 36.5% |
| exec-36 | 6,291ms | 2,293ms | 36.4% |

### C. 용어 정의

| 용어 | 정의 |
|------|------|
| **Bulk Fetch** | 여러 개의 ID를 하나의 쿼리로 한 번에 조회하는 기법 |
| **Hard Parse** | 새로운 SQL 문장을 처음 실행할 때 발생하는 전체 파싱 과정 |
| **Soft Parse** | 캐시된 실행 계획을 재사용하는 파싱 과정 |
| **IN 절 제한** | Oracle에서 IN 절에 포함 가능한 최대 파라미터 개수 (1,000개) |
| **변동계수 (CV)** | 표준편차를 평균으로 나눈 값으로, 상대적 변동성을 나타냄 |

---

**보고서 끝**
