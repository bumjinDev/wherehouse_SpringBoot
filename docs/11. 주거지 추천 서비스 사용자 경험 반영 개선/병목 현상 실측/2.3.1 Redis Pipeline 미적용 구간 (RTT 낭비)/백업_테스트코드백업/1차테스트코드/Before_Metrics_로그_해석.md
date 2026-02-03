# Before 측정 로그 해석 가이드

## 1. 로그 식별자

| 식별자 | 발생 위치 | 설명 |
|--------|----------|------|
| `[Metrics-Sequential]` | `findValidMonthlyPropertiesInDistrict()` | 단일 지역구 처리 결과 |
| `[Metrics-LoopSummary]` | `performMonthlyStrictSearch()` | 25개 지역구 전체 순회 요약 |

---

## 2. `[Metrics-Sequential]` 로그 필드

### 예시 출력
```
[Metrics-Sequential] district=강남구, commands=3, 
  methodTime=2.5432 ms, totalCmdLatency=2.1876 ms, ioRatio=86.02 %, nonIoTime=0.3556 ms, 
  cmd1=0.7234 ms (150건), cmd2=0.6542 ms (80건), cmd3=0.8100 ms (200건), 
  status=SUCCESS|intersection=45
```

### 필드 설명

| 필드 | 단위 | 설명 |
|------|------|------|
| `district` | - | 처리된 지역구명 |
| `commands` | 개수 | 실행된 Redis 명령 수 (1~3, -1은 에러) |
| `methodTime` | ms | 메소드 총 실행 시간 (진입~반환) |
| `totalCmdLatency` | ms | 3개 Redis 명령의 Latency 합계 |
| `ioRatio` | % | `(totalCmdLatency / methodTime) × 100` |
| `nonIoTime` | ms | 비-I/O 시간 (스트림 변환, 교집합 계산 등) |
| `cmd1` | ms | 보증금 조회 Latency |
| `cmd2` | ms | 월세금 조회 Latency |
| `cmd3` | ms | 평수 조회 Latency |
| `(N건)` | 개수 | 해당 ZSet에서 조회된 결과 수 |
| `status` | - | 처리 결과 상태 |

### status 값 의미

| 값 | 설명 |
|----|------|
| `SUCCESS\|intersection=N` | 정상 완료, 교집합 결과 N건 |
| `EARLY_RETURN_DEPOSIT_EMPTY` | 보증금 조회 결과 없음 → 조기 종료 (cmd1만 실행) |
| `EARLY_RETURN_RENT_EMPTY` | 월세금 조회 결과 없음 → 조기 종료 (cmd1,2 실행) |
| `EARLY_RETURN_AREA_EMPTY` | 평수 조회 결과 없음 → 조기 종료 (cmd1,2,3 실행) |
| `ERROR\|ExceptionName` | 예외 발생 |

---

## 3. `[Metrics-LoopSummary]` 로그 필드

### 예시 출력
```
[Metrics-LoopSummary] mode=SEQUENTIAL, totalDistricts=25, 
  successDistricts=18, emptyDistricts=7, totalProperties=1523, 
  loopTime=58.3421 ms, avgPerDistrict=2.3337 ms
```

### 필드 설명

| 필드 | 단위 | 설명 |
|------|------|------|
| `mode` | - | 처리 방식 (SEQUENTIAL = 순차) |
| `totalDistricts` | 개수 | 처리 대상 지역구 수 |
| `successDistricts` | 개수 | 매물이 발견된 지역구 수 |
| `emptyDistricts` | 개수 | 매물이 없는 지역구 수 |
| `totalProperties` | 개수 | 발견된 총 매물 ID 수 |
| `loopTime` | ms | 전체 순회 소요 시간 |
| `avgPerDistrict` | ms | 지역구당 평균 처리 시간 |

---

## 4. 핵심 분석 지표

### 4.1 I/O 비율 해석

```
ioRatio > 80%  → I/O 바운드 상태, Pipeline 적용 효과 큼
ioRatio 50~80% → 혼합 상태, Pipeline 적용 효과 보통
ioRatio < 50%  → CPU 바운드 상태, Pipeline 효과 제한적
```

### 4.2 RTT 추정

```
단일 지역구 RTT 추정 = totalCmdLatency / commands
전체 순회 RTT 추정 = loopTime에서 nonIoTime 비율 고려
```

### 4.3 Early Termination 효과

```
commands=1 → RTT 2회 절약 (조기 종료 효과 발휘)
commands=2 → RTT 1회 절약
commands=3 → 모든 조회 실행
```

---

## 5. Before/After 비교용 데이터 수집

### 수집 항목

| 항목 | Before (순차) | After (Pipeline) | 비교 |
|------|--------------|------------------|------|
| 평균 `totalCmdLatency` | X.XXX ms | X.XXX ms | 개선율 % |
| 평균 `methodTime` | X.XXX ms | X.XXX ms | 개선율 % |
| 평균 `ioRatio` | XX.XX % | XX.XX % | 변화 |
| `loopTime` | X.XXX ms | X.XXX ms | 개선율 % |
| `avgPerDistrict` | X.XXX ms | X.XXX ms | 개선율 % |

### 예상 개선율 (이론값)

```
totalCmdLatency 개선율 = (Before - After) / Before × 100
                       ≈ (3 RTT - 1 RTT) / 3 RTT × 100
                       ≈ 66.67%
```

---

## 6. 로그 분석 팁

1. **Warm-up 효과**: 첫 몇 회의 호출은 JVM/Redis 캐시 영향으로 느릴 수 있음. 5회 이상 반복 후 평균 산출 권장.

2. **지역구별 편차**: 데이터량에 따라 지역구별 Latency 차이 발생. 강남구, 송파구 등 데이터 많은 지역은 상대적으로 느림.

3. **Early Termination 비율**: `emptyDistricts / totalDistricts` 비율이 높으면 Pipeline 적용 시 불필요한 조회 증가 가능. 트레이드오프 분석 필요.

4. **ioRatio 80% 이상**: Pipeline 적용 효과가 극대화되는 구간. 대부분의 시간이 Redis I/O 대기에 사용됨을 의미.
