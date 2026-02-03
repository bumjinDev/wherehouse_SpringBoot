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

테스트 데이터의 매물 개수 분포(54~1,000개)를 기반으로 Hard Parse 최소화와 SQL 호출 횟수 간의 균형점으로 산출하였다. Chunk 22는 Soft Parse 비율 최대화(97.12% 예상)를 목표로 했으나, SQL 호출 횟수 증가(381회)로 Application 오버헤드가 발생했다. Chunk 61은 이론적으로 Hard Parse 15회, SQL 호출 145회, Soft Parse 비율 89.7%로 예상되는 실용적 균형점이다.

| Chunk 크기 | 예상 Hard Parse | 예상 SQL 호출 | 예상 Soft Parse 비율 |
|------------|----------------|--------------|---------------------|
| 1000 (기존) | ~20회 | ~21회 | ~5% |
| 22 (이전) | 11회 | 382회 | 97.12% |
| 61 (현재) | 15회 | 145회 | 89.7% |

---

## 2. V$SYSSTAT 분석

### 2.1 측정 결과

| 통계명 | BEFORE | AFTER | Δ |
|--------|--------|-------|---|
| parse count (total) | 76,823 | 77,026 | +203 |
| parse count (hard) | 6,166 | 6,208 | +42 |
| execute count | 158,476 | 158,813 | +337 |

### 2.2 파생 지표

| 지표 | 측정값 | 예상값 | 평가 |
|------|--------|--------|------|
| Soft Parse 횟수 | 161회 | ~130회 | 예상 초과 |
| Soft Parse 비율 | 79.3% | 89.7% | 예상 미달 |
| Hard Parse 비율 | 20.7% | 10.3% | 예상 초과 |

### 2.3 3개 Chunk 크기 비교

| 지표 | Chunk 1000 | Chunk 22 | Chunk 61 |
|------|-----------|----------|----------|
| parse count (total) Δ | +159 | +1,007 | +203 |
| parse count (hard) Δ | +64 | +173 | +42 |
| execute count Δ | +520 | +3,089 | +337 |
| Soft Parse 비율 | 59.7% | 82.8% | 79.3% |

### 2.4 분석

Hard Parse 42회는 예상(15회)보다 높지만 Chunk 22(173회)의 1/4 수준으로 감소했다. 이는 잔여 Chunk의 바인드 변수 개수가 다양하게 분포하여 추가적인 Hard Parse가 발생했음을 시사한다. execute count +337은 Chunk 22(+3,089) 대비 91% 감소하여 SQL 호출 횟수 최적화가 달성되었다.

---

## 3. V$SQL 분석

### 3.1 SQL_ID 분포

| 지표 | 측정값 | Chunk 22 | 변화 |
|------|--------|----------|------|
| SQL_ID 개수 | 15개 | 11개 | +4개 |
| EXECUTIONS 합계 | 134회 | 360회 | -62.8% |
| 대표 SQL EXECUTIONS | 115회 | 341회 | -66.3% |
| 대표 SQL 비율 | 85.8% | 94.7% | -8.9%p |

### 3.2 Library Cache 재사용 분석

표준 Chunk SQL(61개 바인드 변수, SQL_ID: d9fyvr3kc48ba)이 115회 실행되어 Library Cache에 캐싱되었다. EXECUTIONS(115)와 PARSE_CALLS(114)의 차이가 1회로, Library Cache 재사용이 거의 발생하지 않았다. 이는 동시 20개 요청이 거의 동시에 Library Cache에 접근하면서 Cache Miss가 발생한 것으로 분석된다.

### 3.3 SQL_ID 분포 분석

| SQL_ID | EXEC | PARSE | AVG_MS | BUF_GETS | 구분 |
|--------|------|-------|--------|----------|------|
| d9fyvr3kc48ba | 115 | 114 | 0.69 | 19,273 | 표준 (61개) |
| bqz7jajps49dd | 2 | 2 | 1.67 | 302 | 잔여 |
| 2yg0ksk7nwmtr | 2 | 2 | 1.19 | 46 | 잔여 |
| 7ngjyqjt7nnn3 | 2 | 2 | 1.12 | 199 | 잔여 |
| bq65a0k8btnyz | 2 | 2 | 1.14 | 89 | 잔여 |
| 8ut22shrtxrru | 2 | 2 | 1.28 | 185 | 잔여 |
| (외 9개) | 7 | 7 | 2.18~3.04 | 38~162 | 잔여 |

| 구분 | SQL_ID 개수 | EXECUTIONS 합계 | 비율 |
|------|------------|----------------|------|
| 표준 Chunk (61개) | 1개 | 115회 | 85.8% |
| 마지막 Chunk (잔여) | 14개 | 19회 | 14.2% |

---

## 4. V$SQL_PLAN 실행계획 분석

### 4.1 대표 SQL 실행계획 (SQL_ID: d9fyvr3kc48ba)

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

| 항목 | Chunk 1000 | Chunk 22 | Chunk 61 |
|------|-----------|----------|----------|
| Plan Hash Value | 2173579929 | 437070704 | 437070704 |
| 접근 방식 | TABLE ACCESS FULL | INDEX UNIQUE SCAN | INDEX UNIQUE SCAN |
| Iterator | 없음 | INLIST ITERATOR | INLIST ITERATOR |
| Predicate 위치 | filter (후처리) | access (접근 경로) | access (접근 경로) |
| Cost | 191 | 27 | 68 |
| Rows 추정 | 276 | 22 | 61 |

### 4.4 Predicate Information

IN절이 access 조건으로 처리되어 인덱스 탐색 경로에 직접 활용되었다. Chunk 1000에서는 filter 조건으로 후처리되었으나, Chunk 61에서는 Chunk 22와 동일하게 인덱스 탐색 단계에서 조건이 적용된다.

```
3 - access(("RS1_0"."PROPERTY_ID"=:1 OR ... OR "RS1_0"."PROPERTY_ID"=:61))
```

---

## 5. Application 로그 분석

### 5.1 calculateCharterPropertyScores() 실행 통계

| 지표 | 최소 | 최대 | 평균 | 합계 |
|------|------|------|------|------|
| total_duration_ms | 232ms | 430ms | 317.7ms | 6,354ms |
| rdb_time_ms | 10ms | 134ms | 56.3ms | 1,126ms |
| rdb_time_percent | 4.1% | 36.8% | 17.3% | - |
| rdb_call_count | 1회 | 21회 | 6.7회 | 134회 |

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
| rdb_time_ms 평균 | 30ms | 139.5ms | 56.3ms | -59.6% |
| rdb_time_percent 평균 | 17% | 48.5% | 17.3% | -31.2%p |
| rdb_call_count 합계 | 21회 | 381회 | 134회 | -64.8% |

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

| 지표 | Chunk 1000 | Chunk 22 | Chunk 61 | 변화 (vs 22) |
|------|-----------|----------|----------|-------------|
| CONNECTION_NOT_ADDED 횟수 | 3회 | 192회 | 26회 | -86.5% |
| waiting 최대값 | 1 | 10 | 4 | -60% |
| waiting 평균값 | ~0.3 | 5.99 | 1.85 | -69% |

### 6.3 waiting 값 구간별 분포

| 구간 | 횟수 | 비율 | 의미 |
|------|------|------|------|
| 0 (경쟁 없음) | 0회 | 0% | - |
| 1~2 (경미) | 18회 | 69.2% | 경미한 대기 |
| 3~4 (보통) | 8회 | 30.8% | 보통 경쟁 |
| 5+ (심각) | 0회 | 0% | 미발생 |

Pool Size 6개에 대해 최대 4개의 Thread가 대기하는 상황이 관찰되었다. Chunk 22에서 발생했던 심각한 경쟁(waiting 7~10)이 완전히 해소되었다.

### 6.4 Chunk 22 대비 개선 효과

| 지표 | Chunk 22 | Chunk 61 | 개선 |
|------|----------|----------|------|
| CONNECTION_NOT_ADDED 횟수 | 192회 | 26회 | -86.5% |
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

---

## 7. 종합 분석

### 7.1 테스트 결과 요약

Chunk 61 전략은 DBMS 레벨에서 INLIST ITERATOR + INDEX UNIQUE SCAN 실행계획을 유지하면서, Application 레벨에서 SQL 호출 횟수를 64.8% 감소시켰다. Connection Pool 경쟁도 대폭 완화되어 waiting 최대값이 10에서 4로 감소했으며, 심각한 경쟁(waiting ≥7)이 완전히 해소되었다.

### 7.2 3개 Chunk 크기 종합 비교

| 측면 | Chunk 1000 | Chunk 22 | Chunk 61 | 평가 |
|------|-----------|----------|----------|------|
| **[DBMS 최적화]** | | | | |
| Hard Parse Δ | +64 | +173 | +42 | ✓ 최소 |
| Soft Parse 비율 | 59.7% | 82.8% | 79.3% | ○ 양호 |
| 실행계획 | TABLE ACCESS FULL | INDEX SCAN | INDEX SCAN | ✓ 유지 |
| Cost | 191 | 27 | 68 | ○ 중간 |
| **[Application 영향]** | | | | |
| SQL 호출 횟수 | 21회 | 381회 | 134회 | ○ 개선 |
| RDB 시간 비율 | 17% | 48.5% | 17.3% | ✓ 회복 |
| 총 응답 시간 | 178ms | 268ms | 318ms | △ 증가 |
| **[Connection Pool]** | | | | |
| waiting 최대 | 1 | 10 | 4 | ✓ 개선 |
| 심각 경쟁 비율 | 0% | 47.9% | 0% | ✓ 해소 |

### 7.3 검증된 가설

1. **INLIST ITERATOR 유지**: Chunk 크기 61에서도 옵티마이저가 INDEX UNIQUE SCAN + INLIST ITERATOR를 선택하여 Plan Hash Value 437070704가 Chunk 22와 동일하게 유지되었다.

2. **SQL 호출 횟수 감소**: 381회에서 134회로 64.8% 감소하여 예상값(~145회)에 근접했다. rdb_call_count 합계와 V$SQL EXECUTIONS 합계가 정확히 일치(134회)하여 측정 정합성이 검증되었다.

3. **Connection Pool 경쟁 완화**: waiting 최대값이 10에서 4로 60% 감소했으며, waiting ≥7(심각) 구간이 47.9%에서 0%로 완전히 해소되었다.

4. **RDB 시간 비율 회복**: 48.5%에서 17.3%로 감소하여 Chunk 1000(17%) 수준으로 회복되었다.

### 7.4 한계점 및 교훈

총 응답 시간 증가: Chunk 61(317.7ms)이 Chunk 1000(178ms)보다 78% 높은 이유는 Application 레벨의 Chunking 로직 오버헤드(List 분할, 반복문, Map 병합)와 다중 RDB 호출의 네트워크 라운드트립 비용 때문이다. 단일 쿼리 효율성(Cost 68)보다 총 호출 횟수(134회)가 총 응답 시간에 더 큰 영향을 미쳤다.

Library Cache 재사용 미흡: EXECUTIONS(115) ≈ PARSE_CALLS(114)로 거의 모든 실행에서 Parse Call이 발생했다. 동시 20개 요청의 병렬 접근으로 Cache Hit 기회가 제한되었으며, 실제 운영 환경에서 순차적 요청 시 재사용 효과가 증가할 것으로 예상된다.

### 7.5 최적 전략 권고

| 시나리오 | 권장 Chunk 크기 | 근거 |
|----------|----------------|------|
| Connection Pool 제약 환경 (Pool ≤ 6) | Chunk 61 | 경쟁 완화, 안정성 확보 |
| 응답 시간 최우선 | Chunk 1000 | 최소 호출, 최단 응답 |
| DBMS 최적화 최우선 | Chunk 22 | 최고 Soft Parse 비율 |
| 균형 (권장) | Chunk 61 | DBMS 최적화 유지 + Pool 안정성 |

| 결정 단계 | 검증 항목 | 판단 기준 |
|----------|----------|----------|
| 1단계 | EXPLAIN PLAN 확인 | INLIST ITERATOR + INDEX SCAN 유지 여부 |
| 2단계 | 총 Cost 계산 | 단일 Cost × 예상 호출 횟수 최소화 지점 |
| 3단계 | Connection Pool 검증 | waiting 허용 범위 내 유지 여부 |
| 4단계 | 최종 결정 | 세 조건 모두 만족하는 최대 Chunk 크기 선택 |

### 7.6 결론

#### 7.6.1 Hard Parse 감소와 Soft Parse 비율의 비선형 관계

본 테스트에서 Hard Parse 감소가 Soft Parse 비율 상승을 보장하지 않음을 확인했다. Chunk 22(Hard Parse +173)에서 Chunk 61(Hard Parse +42)로 Hard Parse가 1/4로 감소했음에도 Soft Parse 비율은 오히려 82.8%에서 79.3%로 하락했다. 이 현상의 원인은 Library Cache Hit 기회의 절대량 감소에 있다. Chunk 22는 표준 SQL이 341회 실행되어 Cache Hit 기회가 많았고(실제 11회 재사용), Chunk 61은 115회 실행으로 기회 자체가 줄었다(실제 1회 재사용). 동시성 환경에서 20개 요청이 동시에 Library Cache에 접근하면 첫 번째 요청의 Hard Parse가 완료되기 전에 다른 요청들도 Cache Miss를 경험하므로, SQL 호출 빈도가 낮으면 Cache Hit 확률도 감소한다.

#### 7.6.2 Chunk 크기의 동적 설정 필요성

테스트 데이터의 매물 개수 분포(54~1,000개)가 최적 Chunk 크기를 결정하는 핵심 변수였다. Chunk 1000은 대부분의 요청에서 Chunking이 미발동되었고, Chunk 22는 과도한 SQL 호출을 유발했다. 운영 환경에서는 트래픽 패턴, 데이터 분포, Peak 시간대 특성이 지속적으로 변화하므로, 고정된 Chunk 크기보다는 데이터 크기에 반응하는 적응형 Chunking 전략이 필요하다. 실 트래픽 특성을 모니터링하여 Chunk 크기를 동적으로 조정할 수 있는 설계가 권장된다.

#### 7.6.3 Full Scan 미발생 조건 내 Chunk 크기 최대화 원칙

INLIST ITERATOR + INDEX SCAN 실행계획이 유지되는 한, Chunk 크기 증가는 총 비용(Cost × 호출 횟수) 관점에서 유리하다. Chunk 22의 총 Cost는 27 × 381 = 10,287이고, Chunk 61의 총 Cost는 68 × 134 = 9,112로 11% 감소했다. 단일 쿼리 Cost가 2.5배 상승(27→68)했음에도 호출 횟수가 2.8배 감소(381→134)하여 총 비용이 오히려 낮아졌다.

네트워크 라운드트립 비용 감소가 가장 큰 이점이다. 381회 vs 134회의 네트워크 왕복 비용 차이는 DBMS 내부 Cost 차이보다 훨씬 크며, Application과 DBMS가 물리적으로 분리된 환경에서는 이 효과가 더욱 극대화된다. 또한 SQL 호출 횟수 감소는 Connection 점유 시간 단축으로 이어져 동시성 환경에서의 Pool 경쟁을 완화한다.

#### 7.6.4 Chunk 크기 상한선 결정 기준

Full Scan 전환 임계점은 테이블 크기, 인덱스 선택도, 통계 정보에 따라 달라진다. Oracle 옵티마이저는 IN절 바인드 변수 개수가 증가할수록 Index Scan의 예상 비용을 높게 산정하며, 일반적으로 바인드 변수 개수 × 예상 행 수가 테이블 전체 행 수의 일정 비율(통상 5~15%)을 초과하면 Full Scan이 더 효율적이라고 판단한다. REVIEW_STATISTICS 테이블의 현재 규모에서는 61개가 안전 범위 내에 있었으나, Chunk 크기 증가 시 반드시 실행계획 검증이 필요하다.

#### 7.6.5 종합 결론

Chunk 61 전략은 DBMS 최적화(INLIST ITERATOR, INDEX SCAN)를 유지하면서 Application 오버헤드(SQL 호출 횟수, Connection Pool 경쟁)를 효과적으로 감소시키는 균형점임이 검증되었다. 특히 Pool Size가 제한된 환경에서 Chunk 22의 심각한 경쟁(waiting 10, 심각 47.9%)을 완전히 해소(waiting 4, 심각 0%)하여 시스템 안정성을 확보했다.

핵심 교훈은 세 가지이다. 첫째, Hard Parse 감소가 Soft Parse 비율 상승을 자동으로 보장하지 않으며, Library Cache Hit 기회의 절대량이 중요하다. 둘째, Chunk 크기는 실 트래픽 특성에 따라 동적으로 설정 가능해야 하며, 고정값보다는 적응형 전략이 필요하다. 셋째, Full Scan이 발생하지 않는 조건 내에서 Chunk 크기를 최대화하는 것이 총 비용 관점에서 유리하며, 이를 위해 실행계획 검증이 필수적이다.

현재 테스트 결과를 기반으로 Chunk 61이 Cost 68로 Index Scan을 유지했으므로, 80~100 범위에서 추가 테스트를 통해 Full Scan 전환 임계점 직전의 최적 상한선을 확정하는 것이 향후 과제이다.
