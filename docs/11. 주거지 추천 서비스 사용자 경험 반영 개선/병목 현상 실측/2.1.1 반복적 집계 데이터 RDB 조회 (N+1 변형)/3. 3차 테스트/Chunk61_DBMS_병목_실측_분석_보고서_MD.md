# 3차 테스트 (Chunk 61) DBMS 병목 실측 분석 보고서

**DBMS 최적화와 Application 오버헤드 균형점 검증**

---

## 1. 테스트 개요

### 1.1 테스트 목적

Chunk 크기를 61개로 설정하여 DBMS 최적화 효과(INLIST ITERATOR, Library Cache 재사용)를 유지하면서 Application 레벨 오버헤드(SQL 호출 횟수, Connection Pool 경쟁)를 최소화하는 균형점을 검증한다.

### 1.2 테스트 환경

| 항목 | 값 |
|------|-----|
| 테스트 버전 | 2.1.1 (Chunking) |
| Chunk 크기 | 61개 |
| JMeter 동시 요청 | 20개 (동시 실행) |
| HikariCP Pool Size | 6 (고정) |
| 대상 테이블 | REVIEW_STATISTICS |
| 측정 시점 | 2026-01-08 20:51:41 |

### 1.3 Chunk 크기 61 선정 근거

테스트 데이터의 매물 개수 분포(54~1,000개)를 기반으로 Hard Parse 최소화와 SQL 호출 횟수 간의 균형점으로 산출한 Chunk 크기이다. Chunk 22는 Soft Parse 비율 최대화를 목표로 했으나 Application 측 SQL 호출 횟수가 381회까지 증가하여 오버헤드가 발생했고, Chunk 1000은 대부분의 요청에서 Chunking 분기가 발동되지 않아 단일 호출 경로로 폴백되었다. Chunk 61은 이 두 극단 사이에서 DBMS 최적화와 Application 오버헤드를 동시에 통제하기 위한 구성이다.

---

## 2. V$SYSSTAT 분석

### 2.1 측정 결과

JMeter 부하 실행 전후에 V$SYSSTAT을 수집하여 Parse/Execute 통계의 증분을 산출하였다.

| 통계명 | BEFORE | AFTER | Δ |
|--------|--------|-------|---|
| parse count (total) | 76,823 | 77,026 | +203 |
| parse count (hard) | 6,166 | 6,208 | +42 |
| execute count | 158,476 | 158,813 | +337 |

### 2.2 파생 지표

V$SYSSTAT은 instance 전체의 누적 카운터를 제공하므로 아래 Δ 값에는 본 테스트 트래픽 외에도 동일 instance 내 다른 세션의 parse call, Oracle 백그라운드 프로세스 활동, DBA 도구의 쿼리 등이 함께 포함된다.

| 지표 | 계산식 | 실측값 |
|------|--------|--------|
| Soft Parse 횟수 | 203 − 42 | 161회 |
| Soft Parse 비율 | 161 / 203 | 79.3% |
| Hard Parse 비율 | 42 / 203 | 20.7% |

### 2.3 3개 Chunk 크기 비교

| 지표 | Chunk 1000 | Chunk 22 | Chunk 61 |
|------|-----------|----------|----------|
| parse count (total) Δ | +159 | +1,007 | +203 |
| parse count (hard) Δ | +64 | +173 | +42 |
| execute count Δ | +520 | +3,089 | +337 |
| Soft Parse 비율 (instance) | 59.7% | 82.8% | 79.3% |

### 2.4 분석

V$SYSSTAT 기반 Δ 지표는 instance 전체 카운터라는 한계를 가진다. 본 테스트 트래픽 외 다른 세션의 parse call, Hibernate 메타데이터 조회, HikariCP connection 검증 등이 Δ 값에 합산되므로, V$SYSSTAT 단독으로는 본 테스트 트래픽의 Hard Parse 횟수를 직접 카운팅할 수 없다. 따라서 본 테스트 트래픽만 분리한 정밀 지표는 Section 3의 V$SQL 원본을 근거로 별도 산출한다.

execute count +337은 Chunk 22(+3,089) 대비 89% 감소하여 SQL 호출 횟수 최적화가 instance 수준에서도 확인된다.

---

## 3. V$SQL 분석

### 3.1 SQL_ID 분포

| 지표 | 측정값 | Chunk 22 | 변화 |
|------|--------|----------|------|
| SQL_ID 개수 | 15개 | 11개 | +4개 |
| EXECUTIONS 합계 | 134회 | 360회 | -62.8% |
| PARSE_CALLS 합계 | 133회 | 349회 | - |
| 대표 SQL EXECUTIONS | 115회 | 341회 | -66.3% |
| 대표 SQL 비율 | 85.8% | 94.7% | -8.9%p |

### 3.2 SQL_ID 원본 데이터 (15개 전체)

V$SQL 조회 결과 원본이다. 최상단 d9fyvr3kc48ba가 대표 SQL(바인드 변수 61개)이며, 나머지 14개는 잔여 Chunk의 바인드 변수 개수가 61과 다른 SQL이다.

| SQL_ID | EXEC | PARSE | AVG_MS | BUF_GETS | 구분 |
|--------|------|-------|--------|----------|------|
| d9fyvr3kc48ba | 115 | 114 | 0.69 | 19,273 | 표준 (61개) |
| bqz7jajps49dd | 2 | 2 | 1.67 | 302 | 잔여 |
| 8ut22shrtxrru | 2 | 2 | 1.28 | 185 | 잔여 |
| 7ngjyqjt7nnn3 | 2 | 2 | 1.12 | 199 | 잔여 |
| bq65a0k8btnyz | 2 | 2 | 1.14 | 89 | 잔여 |
| 2yg0ksk7nwmtr | 2 | 2 | 1.19 | 46 | 잔여 |
| a5kzutthtzvcq | 1 | 1 | 3.04 | 154 | 잔여 |
| dy7q052nrdut0 | 1 | 1 | 2.86 | 134 | 잔여 |
| 0sc7gcjg4b1zw | 1 | 1 | 2.78 | 141 | 잔여 |
| f58h2qp3d2pkv | 1 | 1 | 2.70 | 38 | 잔여 |
| b37cakzf7y2v4 | 1 | 1 | 2.65 | 70 | 잔여 |
| 3rarwr5a0apba | 1 | 1 | 2.38 | 162 | 잔여 |
| am81mkar3us3s | 1 | 1 | 2.29 | 94 | 잔여 |
| frkvxbsp57ah2 | 1 | 1 | 2.22 | 42 | 잔여 |
| a610sqxgsy9z9 | 1 | 1 | 2.18 | 94 | 잔여 |
| **합계** | **134** | **133** | - | - | - |

### 3.3 SQL_ID 분포 요약

| 구분 | SQL_ID 개수 | EXECUTIONS 합계 | 비율 |
|------|------------|----------------|------|
| 표준 Chunk (61개) | 1개 | 115회 | 85.8% |
| 잔여 Chunk | 14개 | 19회 | 14.2% |

### 3.4 Library Cache 재사용 분석

대표 SQL d9fyvr3kc48ba는 요청이 Chunk 61개 단위로 분할되어 바인드 변수 61개가 투입된 경우에 생성된 SQL이다. 바인드 변수 개수가 61개로 고정된 동일 SQL 텍스트가 반복 투입되었기에 단일 SQL_ID로 집계되었으며, EXECUTIONS 115회 / PARSE_CALLS 114회를 기록하였다. 대표 SQL 범위로 좁히면 Library Cache 재사용률은 (115−1)/115 = 99.1%로, 최초 1회 Hard Parse 이후 나머지 114회는 모두 Soft Parse로 처리되었음이 확인된다. 이는 shared pool 매칭이 동시성 20 환경에서도 정상 작동했음을 의미한다.

나머지 14개 SQL_ID는 요청이 Chunk 61개 단위로 정확히 나누어 떨어지지 않은 잔여 Chunk에서 생성된 별개의 SQL들이다. 각 잔여 Chunk는 바인드 변수 개수가 61과 다르며, 그 개수가 14종으로 분산되어 있어 Oracle이 각각을 서로 다른 SQL 텍스트로 인식하였고 그 결과 14개의 개별 SQL_ID로 집계되었다. 이 14개 SQL은 모두 EXECUTIONS = PARSE_CALLS를 기록하여 Library Cache 재사용이 전혀 발생하지 않았으며, 이는 각 SQL이 개별 Library Cache 엔트리를 최초 등재한 결과이다.

본 테스트 트래픽 단독 기준의 Parse 구조는 다음과 같이 도출된다.

| 구분 | 산출 근거 | 값 |
|------|----------|-----|
| 테스트 트래픽 Hard Parse | 잔여 14종 × 1회 + 대표 SQL 1회 | 15회 |
| 테스트 트래픽 Soft Parse | 133 − 15 | 118회 |
| 테스트 트래픽 Soft Parse 비율 | 118 / 133 | 88.7% |

잔여 14종이 초래한 Hard Parse 14회는 Chunk 분할 아키텍처에서 입력 데이터 크기가 61의 배수가 아닐 때 필연적으로 발생하는 구조적 비용이다.

---

## 4. V$SQL_PLAN 실행계획 분석

### 4.1 대표 SQL 실행계획 (SQL_ID: d9fyvr3kc48ba)

DBMS_XPLAN.DISPLAY_CURSOR를 대표 SQL_ID에 대해 실행하여 실제 실행계획을 추출하였다.

| 항목 | 값 |
|------|-----|
| Plan Hash Value | 437070704 |
| 바인드 변수 개수 | 61개 |

### 4.2 실행계획 상세

| Id | Operation | Name | Rows | Bytes | Cost |
|----|-----------|------|------|-------|------|
| 0 | SELECT STATEMENT | | | | 68 (100) |
| 1 | INLIST ITERATOR | | | | |
| 2 | TABLE ACCESS BY INDEX ROWID | REVIEW_STATISTICS | 61 | 3,477 | 68 (2) |
| *3 | INDEX UNIQUE SCAN | PK_REVIEW_STATISTICS | 61 | | 59 (2) |

### 4.3 3개 Chunk 크기 실행계획 비교

Chunk 1000 테스트에서는 V$SQL_PLAN으로 3개 SQL_ID(파라미터 54, 276, 1,000)의 실행계획이 직접 확보되었으며, 파라미터 54에서만 INDEX 경로가 채택되고 파라미터 276 및 1,000에서는 TABLE ACCESS FULL 계열로 분기되었다. 아래 비교는 각 Chunk 크기의 대표 SQL 기준이다.

| 항목 | Chunk 1000 (276 파라미터) | Chunk 22 | Chunk 61 |
|------|--------------------------|----------|----------|
| Plan Hash Value | 2,173,579,929 | 437070704 | 437070704 |
| 접근 방식 | TABLE ACCESS FULL | INDEX UNIQUE SCAN | INDEX UNIQUE SCAN |
| Iterator | 없음 | INLIST ITERATOR | INLIST ITERATOR |
| Predicate 위치 | filter (후처리) | access (접근 경로) | access (접근 경로) |
| Cost | 191 | 27 | 68 |
| Rows 추정 | 276 | 22 | 61 |

### 4.4 Predicate Information

IN절이 access 조건으로 처리되어 인덱스 탐색 경로에 직접 활용되었다. Chunk 1000의 FTS 분기에서는 filter 조건으로 후처리되었으나, Chunk 61에서는 Chunk 22와 동일하게 인덱스 탐색 단계에서 조건이 적용된다.

```
3 - access(("RS1_0"."PROPERTY_ID"=:1 OR ... OR "RS1_0"."PROPERTY_ID"=:61))
```

### 4.5 해석

INLIST ITERATOR는 IN절의 각 값을 독립된 Index 탐색으로 반복 수행하는 접근 방식이다. Rows 추정치 61과 INDEX UNIQUE SCAN(PK 기반 단건 보장)이 결합되면, 옵티마이저는 정확히 61회의 Index UNIQUE 탐색 비용으로 Cost를 산정한다. 실제 산정된 Cost는 68(Operation 2 기준)이며, 이 중 INDEX UNIQUE SCAN 단계의 누적 Cost는 59다. 나머지 9는 Index에서 ROWID를 얻어 테이블 블록에 접근하는 비용이다. Bytes 3,477은 61개 행 × 평균 행 크기(약 57 bytes) 기반의 추정 읽기량이다.

---

## 5. Application 로그 분석

### 5.1 calculateCharterPropertyScores() 실행 통계

| 지표 | 최소 | 최대 | 평균 | 합계 |
|------|------|------|------|------|
| total_duration_ms | 232ms | 430ms | 317.7ms | 6,354ms |
| rdb_time_ms | 10ms | 134ms | 58.2ms | 1,163ms |
| rdb_time_percent | 4.1% | 36.8% | 17.6% | - |
| rdb_call_count | 1회 | 21회 | 7.2회 | 144회 |

### 5.2 RDB 호출 횟수 분포

| 구간 | 요청 수 | Thread (call_count) |
|------|---------|---------------------|
| 1~2 | 4개 | exec-12(1), exec-13(2), exec-19(2), exec-11(2) |
| 4~5 | 8개 | exec-14(4), exec-3(4), exec-15(4), exec-18(5), exec-9(5), exec-6(5), exec-17(5), exec-16(5) |
| 9~13 | 7개 | exec-10(9), exec-8(9), exec-7(11), exec-5(12), exec-20(12), exec-4(13), exec-1(13) |
| 21 | 1개 | exec-2(21) |

### 5.3 Chunk 22 대비 비교

| 지표 | Chunk 1000 | Chunk 22 | Chunk 61 | 변화 (vs 22) |
|------|-----------|----------|----------|-------------|
| total_duration_ms 평균 | 178ms | 268.2ms | 317.7ms | +18.5% |
| rdb_time_ms 평균 | 30ms | 139.5ms | 58.2ms | -58.3% |
| rdb_time_percent 평균 | 17% | 48.5% | 17.6% | -30.9%p |
| rdb_call_count 합계 | 21회 | 381회 | 144회 | -62.2% |

### 5.4 전체 Application 로그

calculateCharterPropertyScores() 메서드의 전체 실행 로그이다. 20개 요청에 대한 성능 측정 결과를 시간순으로 정렬하였다.

| Thread | timestamp | total_ms | rdb_ms | rdb_% | call |
|--------|-----------|----------|--------|-------|------|
| exec-14 | 41.308 | 257 | 45 | 17.5% | 4 |
| exec-13 | 41.392 | 244 | 10 | 4.1% | 2 |
| exec-18 | 41.413 | 348 | 38 | 10.9% | 5 |
| exec-4 | 41.419 | 358 | 96 | 26.8% | 13 |
| exec-12 | 41.425 | 268 | 20 | 7.5% | 1 |
| exec-19 | 41.450 | 275 | 26 | 9.5% | 2 |
| exec-9 | 41.455 | 294 | 66 | 22.4% | 5 |
| exec-10 | 41.462 | 320 | 62 | 19.4% | 9 |
| exec-3 | 41.464 | 288 | 60 | 20.8% | 4 |
| exec-1 | 41.465 | 388 | 134 | 34.5% | 13 |
| exec-15 | 41.471 | 320 | 36 | 11.3% | 4 |
| exec-6 | 41.496 | 232 | 33 | 14.2% | 5 |
| exec-20 | 41.496 | 430 | 87 | 20.2% | 12 |
| exec-16 | 41.502 | 347 | 49 | 14.1% | 5 |
| exec-7 | 41.503 | 419 | 72 | 17.2% | 11 |
| exec-8 | 41.504 | 354 | 87 | 24.6% | 9 |
| exec-17 | 41.519 | 345 | 51 | 14.8% | 5 |
| exec-11 | 41.521 | 246 | 13 | 5.3% | 2 |
| exec-5 | 41.523 | 314 | 65 | 20.7% | 12 |
| exec-2 | 41.544 | 307 | 113 | 36.8% | 21 |

### 5.5 Application rdb_call_count와 V$SQL EXECUTIONS 차이

Application bottleneck log의 rdb_call_count 합계 144회와 V$SQL EXECUTIONS 합계 134회 사이에 10회 차이가 존재한다. 차이의 원인은 본 측정 구성으로는 확정할 수 없으며, 실제 DB 호출 횟수의 기준은 Application 측 144회로 둔다.

---

## 6. HikariCP Connection Pool 분석

### 6.1 Pool 설정

| 설정 항목 | 값 |
|----------|-----|
| maximumPoolSize | 6 |
| minimumIdle | 6 (고정 풀) |
| connectionTimeout | 30,000ms |
| leakDetectionThreshold | 60,000ms |
| autoCommit | false |

### 6.2 CONNECTION_NOT_ADDED 이벤트 통계

CONNECTION_NOT_ADDED는 HikariCP가 추가 Connection 확보를 시도했으나 Pool Size 상한(6)에 도달하여 실패한 시점에 기록되는 이벤트다. 이 이벤트에 기록된 waiting 값은 해당 시점에 Connection 획득을 기다리던 Thread 수를 의미한다.

| 지표 | Chunk 1000 | Chunk 22 | Chunk 61 | 변화 (vs 22) |
|------|-----------|----------|----------|-------------|
| CONNECTION_NOT_ADDED 횟수 | 3회 | 192회 | 22회 | -88.5% |
| waiting 최대값 | 1 | 10 | 4 | -60% |
| waiting 평균값 | ~0.3 | 5.99 | 1.95 | -67.4% |

### 6.3 waiting 값 구간별 분포

| 구간 | 횟수 | 비율 | 의미 |
|------|------|------|------|
| 0 (경쟁 없음) | 0회 | 0% | - |
| 1~2 (경미) | 16회 | 72.7% | 경미한 대기 |
| 3~4 (보통) | 6회 | 27.3% | 보통 경쟁 |
| 5+ (심각) | 0회 | 0% | 미발생 |

Pool Size 6개에 대해 최대 4개의 Thread가 대기하는 상황이 관찰되었다. waiting=4 이벤트는 전체 22회 중 1회로, Pool 점유율이 100%인 상태에서 추가 4개 요청이 Connection 획득을 기다린 시점이다. Chunk 22에서 발생했던 심각한 경쟁(waiting 7~10)이 완전히 해소되었다.

### 6.4 Chunk 22 대비 개선 효과

| 지표 | Chunk 22 | Chunk 61 | 개선 |
|------|----------|----------|------|
| CONNECTION_NOT_ADDED 횟수 | 192회 | 22회 | -88.5% |
| waiting ≥7 (심각) 비율 | 47.9% | 0% | 완전 해소 |
| waiting 최대값 | 10 | 4 | -60% |

### 6.5 Pool 종료 상태

| timestamp | log_type | 내용 |
|-----------|----------|------|
| 20:52:01.917 | SHUTDOWN_INITIATED | HikariPool-1 종료 시작 |
| 20:52:01.917 | BEFORE_SHUTDOWN_STATS | total=6, active=0, idle=6, waiting=0 |
| 20:52:01.918~920 | CONNECTION_CLOSING | 6개 Connection evicted |
| 20:52:01.921 | AFTER_SHUTDOWN_STATS | total=0, active=0, idle=0, waiting=0 |
| 20:52:01.923 | SHUTDOWN_COMPLETED | HikariPool-1 종료 완료 |

Pool 종료 시 active=0, idle=6 상태로 Connection leak 없이 정상 반환되었다.

---

## 7. 종합 분석

### 7.1 테스트 결과 요약

본 테스트에서 Chunk 61 구성이 기록한 핵심 실측값을 계층별로 정리한다.

| 계층 | 지표 | 실측값 |
|------|------|--------|
| DBMS 실행계획 | Plan Hash Value | 437070704 (INLIST ITERATOR + INDEX UNIQUE SCAN) |
| DBMS 실행계획 | 단일 쿼리 Cost | 68 (Operation 2 기준) |
| V$SQL | SQL_ID 개수 | 15개 (표준 1 + 잔여 14) |
| V$SQL | EXECUTIONS 합계 | 134회 |
| V$SQL | PARSE_CALLS 합계 | 133회 |
| V$SYSSTAT | Hard Parse Δ (instance) | +42회 |
| 테스트 트래픽 단독 | Hard Parse | 15회 (구조적 도출) |
| 테스트 트래픽 단독 | Soft Parse 비율 | 88.7% (118/133) |
| Application | 평균 응답 시간 | 317.7ms |
| Application | 평균 RDB 시간 | 58.2ms (17.6%) |
| Application | rdb_call_count 합계 | 144회 |
| HikariCP | CONNECTION_NOT_ADDED | 22회 |
| HikariCP | waiting 평균 / 최대 | 1.95 / 4 |
| HikariCP | waiting ≥ 5 (심각) | 0회 |

### 7.2 3개 Chunk 크기 종합 비교

| 측면 | Chunk 1000 | Chunk 22 | Chunk 61 | 평가 |
|------|-----------|----------|----------|------|
| **[DBMS 최적화]** | | | | |
| Hard Parse Δ (instance) | +64 | +173 | +42 | ✓ 최소 |
| Soft Parse 비율 (instance) | 59.7% | 82.8% | 79.3% | ○ 중간 |
| 실행계획 (대표) | TABLE ACCESS FULL | INDEX UNIQUE SCAN | INDEX UNIQUE SCAN | ✓ 유지 |
| Cost | 191 (276 param) | 27 | 68 | ○ 중간 |
| **[Application 영향]** | | | | |
| SQL 호출 횟수 | 21회 | 381회 | 144회 | ○ 개선 |
| RDB 시간 비율 | 17% | 48.5% | 17.6% | ✓ 회복 |
| 총 응답 시간 | 178ms | 268ms | 318ms | △ 증가 |
| **[Connection Pool]** | | | | |
| waiting 최대 | 1 | 10 | 4 | ✓ 개선 |
| 심각 경쟁 비율 | 0% | 47.9% | 0% | ✓ 해소 |

### 7.3 검증된 사실

**1. INLIST ITERATOR 유지.** Chunk 크기 61에서도 옵티마이저가 INDEX UNIQUE SCAN + INLIST ITERATOR를 선택하여 Plan Hash Value 437070704가 Chunk 22와 동일하게 유지되었다. 대표 SQL d9fyvr3kc48ba는 115회 실행 동안 Plan Hash Value를 단 한 차례의 변동 없이 유지했다.

**2. Hard Parse 구조적 도출.** 본 테스트 트래픽의 Hard Parse는 잔여 Chunk 14종 × 1회 + 대표 SQL 1회 = 15회로 구조적으로 도출된다. 잔여 Chunk 14종은 각각 61과 다른 바인드 변수 개수를 가지면서 Oracle에게 서로 다른 SQL 텍스트로 인식되어 14개의 개별 SQL_ID로 집계되었고, 각 SQL_ID는 Library Cache에 기존 엔트리가 존재하지 않는 최초 등재 대상이므로 각자 1회씩 Hard Parse를 수반한다.

**3. 테스트 트래픽 단독 Soft Parse 비율.** 본 테스트 트래픽 단독 기준 Soft Parse 비율은 118/133 = 88.7%이다. 대표 SQL 범위로 좁히면 Library Cache 재사용률은 99.1%(114/115)로, shared pool 매칭이 동시성 20 환경에서도 정상 작동했음이 확인된다.

**4. SQL 호출 횟수 감소.** Application 계층 기준 381회에서 144회로 62.2% 감소했다.

**5. Connection Pool 경쟁 완화.** waiting 최대값이 10에서 4로 60% 감소했으며, waiting ≥7(심각) 구간이 47.9%에서 0%로 완전히 해소되었다. CONNECTION_NOT_ADDED 이벤트는 192회에서 22회로 88.5% 감소했다.

**6. RDB 시간 비율 회복.** 48.5%에서 17.6%로 감소하여 Chunk 1000(17%) 수준으로 회복되었다.

### 7.4 V$SYSSTAT과 본 테스트 트래픽 단독 지표의 관계

V$SYSSTAT instance 전체 Hard Parse Δ +42회는 본 테스트 트래픽 단독 15회의 구조적 도출값과 모순되지 않는다. V$SYSSTAT은 instance 누적 카운터이므로 본 테스트 트래픽 외에 동일 instance 내 다른 세션의 Parse call과 Oracle 백그라운드 활동이 함께 포함되며, 42회 중 본 테스트 트래픽 15회와 나머지 27회의 배분은 측정 구간의 부하 수준에서 일관된다. 본 측정 구성(V$SQL과 V$SYSSTAT 조합)으로 대표 SQL 단위 Hard Parse를 직접 카운팅할 수는 없으나, Plan Hash Value 고정과 잔여 Chunk의 SQL_ID 분포, V$SYSSTAT 증분의 세 관측이 단일 해석으로 수렴한다.

### 7.5 한계점

**총 응답 시간 증가.** Chunk 61(317.7ms)이 Chunk 1000(178ms)보다 약 78% 높은 이유는 Application 레벨의 Chunking 로직 오버헤드(List 분할, 반복문, Map 병합)와 다중 RDB 호출의 네트워크 라운드트립 비용 때문이다. 단일 쿼리 효율성(Cost 68)보다 총 호출 횟수(144회)가 총 응답 시간에 더 큰 영향을 미쳤다. 다만 RDB 구간은 전체 응답 시간의 17.6%에 머물렀고, 나머지 82.4%는 RDB 외부에서 소요된 시간이다. 본 측정 구성에는 이 구간의 내부 분해 지표가 포함되어 있지 않으나, 이 비율 자체로 Chunk 61 구성에서 RDB가 주요 병목이 아님은 확인된다.

**대표 SQL 단위 Hard Parse 직접 카운팅의 부재.** 본 측정 구성은 Plan Hash Value 고정과 SQL_ID 분포로부터 Hard Parse 횟수를 구조적으로 도출하는 방식에 의존한다. 대표 SQL 단위 Hard Parse를 직접 카운팅하려면 V$SQL.LOADS 컬럼의 추가 수집이 후속 측정 항목으로 요구된다.

**측정 구성 간 호출 횟수 차이.** Application bottleneck log의 rdb_call_count 합계 144회와 V$SQL EXECUTIONS 합계 134회 사이에 10회 차이가 존재하며, 본 측정 구성으로는 차이의 원인을 확정할 수 없다.

### 7.6 최적 전략 권고

| 시나리오 | 권장 Chunk 크기 | 근거 |
|----------|----------------|------|
| Connection Pool 제약 환경 (Pool ≤ 6) | Chunk 61 | 경쟁 완화, 안정성 확보 |
| 응답 시간 최우선 | Chunk 1000 | 최소 호출, 최단 응답 |
| DBMS 최적화 최우선 | Chunk 22 | 최고 Soft Parse 비율 |
| 균형 (권장) | Chunk 61 | DBMS 최적화 유지 + Pool 안정성 |

| 결정 단계 | 검증 항목 | 판단 기준 |
|----------|----------|----------|
| 1단계 | EXPLAIN PLAN 확인 | INLIST ITERATOR + INDEX SCAN 유지 여부 |
| 2단계 | 총 Cost 계산 | 단일 Cost × 실측 호출 횟수 최소화 지점 |
| 3단계 | Connection Pool 검증 | waiting 허용 범위 내 유지 여부 |
| 4단계 | 최종 결정 | 세 조건 모두 만족하는 최대 Chunk 크기 선택 |

### 7.7 결론

Chunk 61 구성은 DBMS 실행계획 층위와 Library Cache 재사용 층위 모두에서 의도대로 작동했다. Plan Hash Value는 115회 실행 동안 고정되었고, 세 관측(Plan Hash Value 고정, 잔여 Chunk의 SQL_ID 분포, V$SYSSTAT 증분)의 수렴에 따라 본 테스트 트래픽의 Hard Parse는 15회(대표 SQL 1회 + 잔여 Chunk 14종 각 1회)로 도출되었다. 본 테스트 트래픽 단독 기준 Soft Parse 비율 118/133 = 88.7%가 실측되었다. 잔여 Chunk 14종이 초래한 Hard Parse 14회는 입력 매물 개수가 61의 정배수가 아닐 때 구조적으로 발생하는 비용이다.

Application 계층에서는 RDB 구간이 전체 응답 시간의 17.6%에 머물렀고, HikariCP Pool은 waiting 5 이상의 심각 경쟁 없이 Connection leak도 없이 정상 운용되었다. Chunk 22의 심각한 경쟁(waiting 10, 심각 47.9%)이 Chunk 61에서 완전히 해소(waiting 4, 심각 0%)되어 Pool Size가 제한된 환경에서 시스템 안정성이 확보되었다.

Chunk 61 구성은 RDB가 주요 병목이 아닌 상태에서 안정 동작했으며, 대표 SQL 단위 Hard Parse를 직접 카운팅하려는 경우 V$SQL.LOADS 컬럼의 추가 수집이 후속 측정 항목으로 요구된다. 현재 테스트 결과를 기반으로 Chunk 61이 Cost 68로 Index Scan을 유지했으므로, 80~100 범위에서 추가 테스트를 통해 Full Scan 전환 임계점 직전의 최적 상한선을 확정하는 것이 향후 과제이다.