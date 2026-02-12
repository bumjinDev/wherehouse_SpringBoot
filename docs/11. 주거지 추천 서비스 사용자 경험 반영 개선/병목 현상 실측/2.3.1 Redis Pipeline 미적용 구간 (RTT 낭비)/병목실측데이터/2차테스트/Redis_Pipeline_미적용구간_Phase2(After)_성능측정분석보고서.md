# Redis Pipeline 적용 Phase 2 (After) 성능 측정 분석 보고서

## 문서 정보

| 항목 | 내용 |
|------|------|
| 보고서 목적 | `findValidMonthlyPropertiesInDistrict()` 메서드에 Redis Pipeline을 적용한 뒤 After 성능 실측 및 Phase 1 (Before) 대비 개선 효과 정량 분석 |
| 대상 병목 | 2.3.1 Redis Pipeline 미적용 구간 (RTT 낭비) |
| 측정 일시 | 2026-02-12 19:50:13 ~ 19:50:18 |
| 작성 버전 | v1.0 (Phase 2 After 단독 + Before-After 비교) |
| 관련 계획서 | `Redis_Pipeline_미적용_구간_병목_해결_계획서_v2.md` |
| 선행 보고서 | `Redis_Pipeline_미적용구간_Phase1_Before_성능측정분석보고서.md` |

---

## 1. 측정 환경

### 1.1 하드웨어 및 네트워크 구성

| 구성 요소 | 사양 |
|-----------|------|
| 클라이언트 | 172.20.10.2 (로컬 개발 머신) |
| Redis 서버 | 61.75.54.208:6379 (원격 서버) |
| 네트워크 경로 | 클라이언트 → 인터넷 → 원격 Redis 서버 (loopback이 아닌 실제 원격 구간) |

Phase 1과 동일한 물리 환경이다. Layer 2 Wireshark 캡처에서 확인된 TCP 연결 대상은 Phase 1과 동일한 `61.75.54.208:6379`이다.

### 1.2 소프트웨어 구성

| 항목 | 값 |
|------|-----|
| JDK | Java 17 |
| Framework | Spring Boot 3.x, Spring Data Redis (Lettuce 기반) |
| Redis Client | Lettuce (Netty 기반 비동기 드라이버, 동기 API 사용) |
| 실행 스레드 | `http-nio-8185-exec-1` (단일 스레드, 순차 처리) |

### 1.3 측정 도구

| 계층 | 도구 | 정밀도 | 측정 대상 |
|------|------|--------|-----------|
| Layer 1 (애플리케이션) | `System.nanoTime()` | 나노초 | Pipeline Latency, Method Time, I/O 비율 |
| Layer 2 (네트워크) | Wireshark (pcapng) | 나노초 | TCP 패킷 타임스탬프 기반 Req-Resp Latency, Total Transfer |
| 로그 파서 | `RTT_Test_Phase2_parse.py` | — | `[Metrics-Pipeline]`, `[Metrics-LoopSummary]` 로그 추출 및 Excel 변환 |

### 1.4 테스트 요청 조건

| 항목 | 값 | Phase 1과 동일 여부 |
|------|-----|:---:|
| 임대료 형태 | 월세 | ✅ |
| 보증금 범위 | 500 ~ 10,000 만원 | ✅ |
| 월세 범위 | 10 ~ 150 만원 | ✅ |
| 평수 범위 | 10.0 ~ 60.0 평 | ✅ |
| 우선순위 | 1순위 PRICE(60%), 2순위 SAFETY(30%), 3순위 SPACE(10%) | ✅ |
| 예산 유연성 | 0% | ✅ |
| 최소 안전 점수 | 0점 | ✅ |
| 절대 최소 평수 | 0평 | ✅ |

Phase 1과 동일한 테스트 요청 조건이다. 실측 결과 25개 지역구 전체에서 `status=SUCCESS`(Early Return 0건)로 확인되었으므로, 75개 Command 전체에 대한 완전한 측정 데이터셋이 확보되었다.

---

## 2. 측정 대상 코드 구조

### 2.1 변경 사항 요약

| 항목 | Phase 1 (Before) | Phase 2 (After) |
|------|-------------------|------------------|
| Redis 호출 방식 | `opsForZSet().rangeByScore()` × 3회 순차 호출 | `redisTemplate.executePipelined(RedisCallback)` 단일 호출 |
| RTT 횟수 (지역구당) | 3회 | 1회 |
| Early Termination | Command 사이 3곳에서 빈 결과 체크 | Pipeline 실행 후 사후 일괄 체크 1곳 |
| 결과 타입 | `Set<Object>` | `Set<?>` → `convertToStringSet()` 변환 |
| 측정 태그 | `[Metrics-Sequential]` | `[Metrics-Pipeline]` |
| 측정 지점 | `cmd1Start/cmd1End`, `cmd2Start/cmd2End`, `cmd3Start/cmd3End` 3쌍 | `pipelineStart/pipelineEnd` 1쌍 |

### 2.2 Pipeline 적용 코드 구조

```java
// Pipeline 실행: 3개 명령을 단일 RTT로 전송
long pipelineStart = System.nanoTime();

List<Object> pipelineResults = redisHandler.redisTemplate.executePipelined(
    (RedisCallback<Object>) connection -> {
        connection.zRangeByScore(depositKey.getBytes(UTF_8), budgetMin, budgetMax);
        connection.zRangeByScore(monthlyRentKey.getBytes(UTF_8), monthlyRentMin, monthlyRentMax);
        connection.zRangeByScore(areaKey.getBytes(UTF_8), areaMin, areaMax);
        return null;
    }
);

long pipelineEnd = System.nanoTime();
pipelineLatency = pipelineEnd - pipelineStart;

// 결과 추출 (인덱스 순서 = 명령 실행 순서)
Set<?> depositRaw = (Set<?>) pipelineResults.get(0);
Set<?> rentRaw = (Set<?>) pipelineResults.get(1);
Set<?> areaRaw = (Set<?>) pipelineResults.get(2);
```

`executePipelined()` 내부에서 3개 `connection.zRangeByScore()`가 write buffer에 순차 적재된 뒤, 콜백 반환 시점에 TCP 소켓으로 일괄 flush된다. Redis 서버는 수신한 3개 Command를 순서대로 처리하고, 3개의 RESP Array 응답을 하나의 TCP 스트림으로 반환한다. 클라이언트 측에서는 하나의 네트워크 왕복으로 3개 응답을 수신하므로, RTT가 3회에서 1회로 감소한다.

### 2.3 Serializer 호환성 처리

Pipeline은 low-level `connection.zRangeByScore()`를 사용하므로, RedisTemplate의 Serializer 설정에 따라 반환 타입이 `byte[]` 또는 `String`으로 달라진다. 이를 처리하기 위해 런타임 타입을 확인하여 `Set<String>`으로 통일하는 `convertToStringSet()` 메서드가 추가되었다.

### 2.4 측정 계측 방식

Pipeline 호출 전후의 `System.nanoTime()` 차이로 Pipeline Latency를 산출하고, 메서드 진입~종료 구간의 Method Time, 그리고 `(Pipeline Latency / Method Time) × 100`으로 I/O 비율을 산출한다. 로그 포맷은 `[Metrics-Pipeline]` 태그로 출력된다.

```
[Metrics-Pipeline] district=종로구, commands=3(batched),
  methodTime=105.1374 ms, pipelineLatency=104.5450 ms, ioRatio=99.44 %,
  nonIoTime=0.5924 ms,
  result1=91 건, result2=139 건, result3=128 건,
  status=SUCCESS|intersection=12
```

---

## 3. Layer 1 측정 결과 (애플리케이션 레벨: Pipeline Latency)

### 3.1 전체 루프 요약

| 지표 | 값 |
|------|---:|
| 대상 지역구 수 | 25개 |
| 3개 Command 모두 실행된 지역구 (SUCCESS) | 25개 (100%) |
| 빈 결과 반환 (EMPTY) | 0건 |
| 검색된 총 매물 ID 수 (교집합 합계) | 13,942건 |
| 루프 총 소요 시간 | 4,319.99 ms |
| 지역구 평균 소요 시간 | 172.80 ms |

### 3.2 지역구별 상세 데이터 (원본)

아래 표는 `[Metrics-Pipeline]` 로그에서 추출한 25개 지역구 전체의 측정 원본이다. MethodTime 내림차순 정렬.

| # | 지역구 | MethodTime (ms) | PipelineLatency (ms) | I/O비율 (%) | NonIO (ms) | R1 건수 | R2 건수 | R3 건수 | 교집합 |
|---|--------|----------------:|---------------------:|------------:|----------:|--------:|--------:|--------:|-------:|
| 1 | **강남구** | **2,894.51** | **2,875.19** | **99.33** | **19.32** | **16,008** | **36,251** | **51,211** | **11,287** |
| 2 | 송파구 | 108.33 | 108.08 | 99.77 | 0.25 | 367 | 638 | 973 | 194 |
| 3 | 종로구 | 105.14 | 104.55 | 99.44 | 0.59 | 91 | 139 | 128 | 12 |
| 4 | 노원구 | 95.60 | 95.29 | 99.68 | 0.31 | 623 | 816 | 752 | 496 |
| 5 | 강동구 | 80.39 | 80.11 | 99.65 | 0.28 | 354 | 648 | 662 | 172 |
| 6 | 강서구 | 80.21 | 79.58 | 99.21 | 0.63 | 434 | 656 | 599 | 291 |
| 7 | 중구 | 63.14 | 62.99 | 99.76 | 0.15 | 160 | 166 | 168 | 18 |
| 8 | 관악구 | 59.21 | 59.07 | 99.77 | 0.14 | 200 | 290 | 229 | 67 |
| 9 | 서초구 | 54.47 | 54.25 | 99.60 | 0.22 | 219 | 423 | 801 | 97 |
| 10 | 마포구 | 53.62 | 53.42 | 99.62 | 0.20 | 319 | 422 | 600 | 117 |
| 11 | 동대문구 | 53.09 | 52.79 | 99.43 | 0.30 | 295 | 404 | 342 | 80 |
| 12 | 성동구 | 52.81 | 52.67 | 99.74 | 0.14 | 190 | 293 | 494 | 24 |
| 13 | 성북구 | 51.09 | 50.94 | 99.71 | 0.15 | 224 | 347 | 372 | 97 |
| 14 | 동작구 | 50.50 | 50.37 | 99.74 | 0.13 | 178 | 322 | 346 | 41 |
| 15 | 구로구 | 50.10 | 49.83 | 99.47 | 0.27 | 289 | 469 | 388 | 163 |
| 16 | 은평구 | 49.15 | 48.87 | 99.43 | 0.28 | 240 | 362 | 309 | 95 |
| 17 | 서대문구 | 47.94 | 47.81 | 99.73 | 0.13 | 194 | 293 | 325 | 70 |
| 18 | 강북구 | 47.05 | 46.95 | 99.80 | 0.09 | 122 | 143 | 104 | 40 |
| 19 | 용산구 | 46.82 | 46.69 | 99.72 | 0.13 | 134 | 174 | 265 | 29 |
| 20 | 영등포구 | 46.39 | 46.17 | 99.52 | 0.22 | 223 | 406 | 405 | 70 |
| 21 | 양천구 | 46.26 | 46.11 | 99.67 | 0.15 | 179 | 393 | 489 | 123 |
| 22 | 중랑구 | 45.65 | 45.43 | 99.51 | 0.22 | 255 | 434 | 295 | 132 |
| 23 | 광진구 | 42.26 | 42.11 | 99.66 | 0.15 | 97 | 223 | 228 | 10 |
| 24 | 금천구 | 41.39 | 41.30 | 99.79 | 0.09 | 95 | 181 | 113 | 35 |
| 25 | 도봉구 | 40.65 | 40.50 | 99.64 | 0.14 | 214 | 286 | 264 | 182 |

### 3.3 통계 요약

#### 전체 25개 지역구

| 지표 | 값 |
|------|---:|
| 총 Pipeline Latency (합계) | 4,281.09 ms |
| 평균 MethodTime (지역구당) | 172.23 ms |
| 평균 Pipeline Latency (지역구당) | 171.24 ms |
| 평균 I/O 비율 | 99.62% |
| 총 RTT 발생 횟수 | 25회 |

#### 강남구 제외 24개 지역구

| 지표 | 값 |
|------|---:|
| 평균 MethodTime | 58.80 ms |
| 중앙값 MethodTime | 50.80 ms |
| 최소 MethodTime | 40.65 ms (도봉구) |
| 최대 MethodTime | 108.33 ms (송파구) |
| 표준편차 | 19.89 ms |
| 평균 Pipeline Latency | 58.58 ms |
| 평균 I/O 비율 | 99.63% |

#### NonIO 시간 통계 (강남구 제외 24개 지역구)

| 지표 | 값 |
|------|---:|
| 평균 | 0.22 ms |
| 최소 | 0.09 ms |
| 최대 | 0.63 ms |

NonIO 시간은 Redis Key 구성, Pipeline 결과 추출(`pipelineResults.get()`), `convertToStringSet()` 변환(byte[]→String 또는 Object→String), `retainAll()` 교집합 연산의 비용이다. 전체 MethodTime(40–108ms) 대비 0.1–0.8% 수준이다.

### 3.4 강남구 이상치 분석

강남구 MethodTime(2,894.51ms)은 나머지 24개 지역구 평균(58.80ms)의 약 49.2배다.

| 지역구 | R1 반환건수 | R2 반환건수 | R3 반환건수 | 총 반환건수 | MethodTime (ms) |
|--------|----------:|----------:|----------:|-----------:|---------------:|
| 강남구 | 16,008 | 36,251 | 51,211 | 103,470 | 2,894.51 |
| 노원구 (최대 반환건수 2위) | 623 | 816 | 752 | 2,191 | 95.60 |
| 도봉구 (최소 MethodTime) | 214 | 286 | 264 | 764 | 40.65 |

Phase 1에서 강남구 MethodTime은 3,428.54ms였고, Phase 2에서 2,894.51ms로 534.03ms(15.6%) 감소하였다. 이 절감분의 구성은 다음과 같다. Phase 1에서 강남구의 3개 Command Req-Resp Latency 합계는 113.6ms(Layer 2 실측)였고, Pipeline 적용 후 1회 Req-Resp Latency는 47.8ms이므로 RTT 절감분은 약 65.8ms이다.
그러나 강남구 MethodTime(2,894ms)은 여전히 전체 LoopTime(4,320ms)의 67%를 차지한다. 병목의 본질은 103,470건(4.0MB) 응답 데이터의 네트워크 전송 시간이며, Pipeline은 RTT를 줄이는 기법이므로 payload 볼륨 병목에 대한 근본적 해결은 아니다.

---

## 4. Layer 2 측정 결과 (네트워크 레벨: Wireshark 패킷 분석)

### 4.1 측정 목적

Phase 1과 동일한 목적이다. Layer 1의 Pipeline Latency는 순수 네트워크 왕복 시간 외에 직렬화/역직렬화 비용이 혼재되어 있다. Layer 2에서 TCP 패킷 타임스탬프를 캡처하여 Java 런타임 오버헤드가 포함되지 않은 순수 네트워크 계층 시간 데이터를 확보한다.

Phase 2에서의 추가 목적은 Pipeline 적용에 따른 패킷 구조 변화를 확인하는 것이다. Sequential 방식에서는 지역구당 3개의 독립 요청-응답 쌍이 존재했으나, Pipeline에서는 지역구당 1개의 요청 패킷에 3개 Command가 포함되고, 응답은 3개 RESP Array가 하나의 TCP 스트림으로 수신된다.

### 4.2 캡처 환경

| 항목 | 값 |
|------|-----|
| 캡처 도구 | Wireshark (pcapng) |
| 캡처 지점 | 클라이언트 측 네트워크 인터페이스 |
| 캡처 필터 | `tcp.port == 6379 && tcp.len > 0` (데이터 패킷만, ACK-only 제외) |
| 클라이언트 | 172.20.10.2 |
| Redis 서버 | 61.75.54.208:6379 |

### 4.3 캡처 패킷 통계

| 항목 | 값 |
|------|---:|
| Pipeline batch 수 | 25개 (지역구당 1회) |
| 요청 패킷 (Pipeline 패킷, 3 Command 포함) | 25개 |
| 응답 패킷 (3개 RESP Array 분할 전송) | 3,856개 |
| 총 응답 바이트 | 5,237,998 bytes (약 5.0 MB) |

Phase 1에서는 요청 패킷 75개, 응답 패킷 3,852개였다. Phase 2에서는 요청 패킷이 75개→25개로 감소하였고, 응답 패킷 수(3,856개)는 Phase 1(3,852개)과 거의 동일하다. 응답 데이터 총량이 동일하므로 TCP segmentation에 의한 응답 패킷 수는 변하지 않으며, 요청 패킷 수만 1/3로 감소한 것이 Pipeline의 구조적 효과다.

### 4.4 측정 지표 정의

Layer 2에서 산출하는 3개 시간 지표. Phase 1에서는 Command 단위로 측정했으나, Phase 2에서는 Pipeline batch 단위로 측정한다.

**Req-Resp Latency**: Pipeline 요청 패킷 전송 시점 ~ 첫 번째 응답 패킷 도착 시점. 네트워크 편도 전파(Client→Server) + Redis 서버 3개 Command 일괄 처리 + 네트워크 편도 전파(Server→Client)를 포함한다. Pipeline에서는 3개 Command를 한 번의 왕복으로 처리하므로, Sequential 대비 RTT 2회분이 절감된다.

**Total Transfer**: Pipeline 요청 패킷 전송 시점 ~ 3개 RESP 응답의 마지막 TCP 세그먼트 도착 시점. 3개 Command의 응답 합산 데이터 볼륨에 비례하여 증가한다.

**Payload Delivery**: `Total Transfer - Req-Resp Latency`. 3개 Command 응답의 후속 TCP 세그먼트 전송 시간이다.

### 4.5 Req-Resp Latency 통계

25개 Pipeline batch의 Req-Resp Latency 대표값:

| 지표 | 전체 25개 batch | 강남구 제외 24개 batch |
|------|----------------:|-----------------------:|
| 평균 | 39.251 ms | 38.893 ms |
| 중앙값 | 36.179 ms | 36.069 ms |
| 최소 | 32.454 ms (동대문구) | 32.454 ms (동대문구) |
| 최대 | 56.554 ms (송파구) | 56.554 ms (송파구) |

Phase 1에서 75개 개별 Command의 Req-Resp Latency 평균은 38.492ms였다. Phase 2에서 25개 Pipeline batch의 Req-Resp Latency 평균은 39.251ms이다. 차이는 0.759ms로, Pipeline 1회 왕복의 Req-Resp Latency가 Sequential 단일 Command의 Req-Resp Latency와 사실상 동일함을 입증한다. Redis 서버가 3개 Command를 일괄 처리하는 데 추가되는 시간은 1ms 미만이며, RTT에 비해 무시할 수 있는 수준이다.

### 4.6 강남구 전송 시간 상세

| 지표 | 값 |
|------|---:|
| Req-Resp Latency | 47.832 ms |
| Total Transfer | 2,791.659 ms |
| Payload Delivery | 2,743.827 ms |
| 응답 패킷 수 | 3,106개 |
| 응답 바이트 (합계) | 4,242,294 bytes (4.0 MB) |

| RESP 응답 | 대상 Command | 응답 바이트 | Result Count |
|-----------|-------------|----------:|------------:|
| Response 1 | deposit(보증금) | 656,336 | 16,008 |
| Response 2 | monthlyRent(월세) | 1,486,299 | 36,251 |
| Response 3 | area(평수) | 2,099,659 | 51,211 |
| **합계** | | **4,242,294** | **103,470** |

강남구의 Req-Resp Latency(47.832ms)는 다른 지역구 평균(38.893ms)과 유사한 수준이다. Pipeline 일괄 처리에 의한 RTT는 정상 범위이다. Total Transfer(2,791.659ms)가 극단적으로 높은 이유는 103,470건(4.0MB) 응답 데이터의 Payload Delivery Time(2,743.827ms)이며, 이는 Pipeline 적용과 무관한 순수 데이터 전송 비용이다.

Phase 1에서 강남구의 3개 Command Total Transfer 합계는 3,330ms였다. Phase 2에서 2,792ms로 539ms(16.2%) 감소하였다. Sequential에서는 각 Command 응답 수신 후 다음 요청을 보내므로 TCP 연결이 idle 상태를 거치지만, Pipeline에서는 서버가 3개 응답을 연속 전송하므로 TCP window가 idle 없이 효율적으로 활용된 결과로 해석된다.

---

## 5. Before-After 비교 분석

### 5.1 Layer 1 핵심 지표 비교

| 지표 | Phase 1 (Before) | Phase 2 (After) | 절감량 | 개선율 |
|------|------------------:|------------------:|-------:|-------:|
| 루프 총 시간 | 6,540.59 ms | 4,319.99 ms | 2,220.60 ms | **34.0%** |
| 강남구 제외 평균 MethodTime | 128.95 ms | 58.80 ms | 70.15 ms | **54.4%** |
| 강남구 제외 평균 Cmd/Pipeline Latency | 128.59 ms | 58.58 ms | 70.01 ms | **54.4%** |
| 강남구 MethodTime | 3,428.54 ms | 2,894.51 ms | 534.03 ms | 15.6% |
| 평균 I/O 비율 | 99.71% | 99.62% | −0.09%p | — |
| 총 RTT 횟수 | 75회 | 25회 | 50회 | **66.7%** |

강남구를 제외한 24개 지역구에서 지역구당 평균 MethodTime이 128.95ms → 58.80ms로 **54.4% 감소**하였다. 이는 3회 순차 RTT에서 1회 Pipeline RTT로 전환함으로써 RTT 2회분의 네트워크 대기 시간이 제거된 결과다.

### 5.2 Layer 2 핵심 지표 비교

| 지표 | Phase 1 (Before) | Phase 2 (After) | 비고 |
|------|------------------:|------------------:|------|
| 요청 패킷 수 | 75개 | 25개 | 66.7% 감소 |
| 단일 Req-Resp Latency 평균 | 38.492 ms | 39.251 ms | 차이 0.759ms (동일 수준) |
| 지역구당 총 Req-Resp Latency (강남구 제외) | 115.55 ms (3회분) | 38.89 ms (1회분) | **66.3% 감소** |

Phase 1에서 단일 Command의 Req-Resp Latency 평균은 38.492ms, Phase 2에서 Pipeline batch의 Req-Resp Latency 평균은 39.251ms로 사실상 동일하다. Pipeline이 RTT 횟수를 줄이면서도 1회 왕복의 네트워크 비용을 증가시키지 않았음을 Layer 2에서 입증한다.

---

## 6. 분석 종합

### 6.1 Pipeline 적용 효과 확정

강남구를 제외한 24개 지역구에서 지역구당 평균 MethodTime이 **128.95ms → 58.80ms (54.4% 감소)**로 측정되었다. 루프 전체 시간은 **6,540.59ms → 4,319.99ms (34.0% 감소)**이다. RTT 횟수는 75회 → 25회로 **66.7% 감소**하였으며, Layer 2에서 Pipeline 1회 Req-Resp Latency(39.3ms)가 Sequential 단일 Command Req-Resp Latency(38.5ms)와 동일 수준임이 입증되었다. Pipeline은 RTT 횟수를 1/3로 줄이면서도 1회 왕복 비용을 증가시키지 않는 구조적 개선이다.

### 6.2 I/O 바운드 특성 유지

Phase 2에서도 I/O 비율이 99.21%–99.80%(전 지역구)로 측정되어, Pipeline 적용 후에도 여전히 I/O 바운드 워크로드임이 확인된다.

### 6.3 강남구 한계 확인

강남구의 MethodTime은 15.6% 감소에 그쳤으며, LoopTime 비중은 오히려 52.4% → 67.0%로 증가하였다. 103,470건(4.0MB) 응답 데이터의 네트워크 전송 시간이 병목의 본질이며, RTT 횟수를 줄이는 Pipeline으로는 해결할 수 없다. 후속 개선으로 강남구의 payload 볼륨을 줄일 수 있는 알고리즘을 별도로 고민해야 한다.

---

## 부록 A. 용어 정의

| 용어 | 정의 |
|------|------|
| Pipeline Latency | `executePipelined()` 호출 시작~반환까지의 총 소요 시간. 3개 Command의 일괄 전송, 네트워크 왕복, 전체 응답 수신, 결과 수집 포함 |
| Method Time | 메서드 진입(Key 구성 시작)~로그 출력 시점까지의 총 소요 시간. Pipeline Latency + NonIO 시간 |
| I/O 비율 | `(Pipeline Latency / Method Time) × 100`. I/O 바운드 정도를 나타내는 지표 |
| Req-Resp Latency | Wireshark 기준 Pipeline 요청 패킷 전송~첫 응답 패킷 도착 시간. 순수 네트워크 왕복 + Redis 서버 3개 Command 일괄 처리 시간 |
| Total Transfer | Wireshark 기준 Pipeline 요청 패킷 전송~마지막 응답 패킷(3개 RESP Array 완료) 도착 시간 |
| Payload Delivery | `Total Transfer - Req-Resp Latency`. 후속 TCP 세그먼트의 물리적 전송 시간 |
| NonIO 시간 | Method Time에서 Pipeline Latency를 뺀 값. Key 구성, 결과 추출, byte[]→String 변환, retainAll() 교집합 연산 등의 비용 |
| RTT | Round-Trip Time. 네트워크 패킷의 왕복 시간 |

## 부록 B. 원본 데이터 출처

| 데이터 | 파일명 | 위치 |
|--------|--------|------|
| Layer 1 (애플리케이션 로그) 원본 | `wherehouse_phase2.log` | 병목실측데이터/2차테스트/ |
| Layer 1 Excel | `Application_Log_Test_Phase2_result.xlsx` | 병목실측데이터/2차테스트/ |
| Layer 2 (Wireshark 분석) | `Wireshark_Phase2_Layer2_Analysis.xlsx` | 병목실측데이터/2차테스트/ |
| Phase 1 보고서 | `Redis_Pipeline_미적용구간_Phase1_Before_성능측정분석보고서.md` | 병목실측데이터/1차테스트/ |
| 계획서 | `Redis_Pipeline_미적용_구간_병목_해결_계획서_v2.md` | 병목실측계획서/ |
| 측정 대상 소스코드 (Phase 2) | `MonthlyRecommendationService_Test_Phase2.java` | wherehouse/src/main/java/com/wherehouse/recommand/service/ |
| 로그 파서 (Phase 2) | `RTT_Test_Phase2_parse.py` | 병목실측데이터/2차테스트/ |

## 부록 C. Phase 1 — Phase 2 동일 데이터셋 검증

Before-After 비교의 전제 조건인 "동일 데이터셋에 대한 동일 쿼리"를 검증한 결과:

| 검증 항목 | 검증 건수 | 결과 |
|-----------|--------:|------|
| 3개 Command 반환 건수 (R1, R2, R3) | 25 × 3 = 75개 | 전부 일치 |
| 교집합 건수 (Intersection) | 25개 | 전부 일치 |
| 검색된 총 매물 ID 수 | 1건 | 13,942건 (동일) |

Redis 데이터셋이 Phase 1과 Phase 2 측정 사이에 변경되지 않았으며, 동일 요청 조건에서 동일 결과가 반환되었음이 정량적으로 입증되었다.