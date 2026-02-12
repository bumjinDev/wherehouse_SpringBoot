# Wireshark Phase 2 (Pipeline) Layer 2 분석 엑셀 생성 지시문

> 이 문서와 함께 **pcapng 파일**과 **Application_Log_Test_Phase2_result.xlsx** (L1 원본)을 첨부하여 새 세션에서 지시하라.

---

## 지시 프롬프트 (아래를 복사하여 사용)

```
첨부한 두 파일을 사용하여 Wireshark Phase 2 (Pipeline) Layer 2 분석 엑셀 파일을 생성하라.

■ 입력 파일
1. pcapng 파일: Redis Pipeline 적용 후, 25개 서울 지역구 × 1 Pipeline batch(3 Command 묶음)의 패킷 캡처
2. Application_Log_Test_Phase2_result.xlsx: Layer 1(Application Log) 측정 결과. "Pipeline_Metrics" 시트에서 지역구별 MethodTime(ms), PipelineLatency(ms), IO_Ratio(%), NonIO(ms), Result1_Count~Result3_Count 값 사용

■ 환경 정보
- Redis 서버: 61.75.54.208:6379 (원격)
- 클라이언트: 172.20.10.2
- 프로토콜: RESP (Redis Serialization Protocol)
- 요청 모드: Pipeline (지역구당 ZRANGEBYSCORE 3개를 하나의 TCP 패킷에 묶어 전송)
  - Key 패턴: idx:{type}:{district} 또는 idx:{type}:{district}:월세
  - type 순서: deposit(보증금) → monthlyRent(월세) → area(평수)
- 총 요청 패킷: 25개 (지역구당 1개, 각 패킷에 ZRANGEBYSCORE 3개 포함)

■ 패킷 파싱 방법
1. tshark로 tcp.port == 6379 && tcp.len > 0 필터 적용하여 데이터 패킷 추출
2. 요청 패킷(클라이언트→서버, dstport=6379)에서 TCP payload를 RESP 디코딩하여 ZRANGEBYSCORE가 3개 포함된 Pipeline 요청 25개 식별
3. 응답 패킷(서버→클라이언트, sport=6379) 매핑:
   - 요청 N의 응답 시작 = 요청 N 전송 시점 이후 최초 서버→클라이언트 패킷
   - 요청 N의 응답 끝 = RESP 프로토콜 파싱 기반으로 3개의 최상위 Array(*N) 응답이 모두 완료된 시점의 패킷
   - RESP 파싱 방법: 서버 패킷의 TCP payload를 순서대로 연결(TCP stream 재조립)하면서, 최상위 RESP 응답이 3개 완료되는 시점을 감지한다. RESP Array(*N), Bulk String($N), Simple String(+), Error(-), Integer(:) 각 타입의 완전성을 재귀적으로 검증한다.
   - 마지막 요청(25번째)도 동일하게 RESP 파싱으로 3개 응답 완료 시점을 식별한다. 이후 패킷(Lettuce heartbeat 등)은 자동 제외된다.

■ 측정 지표 정의 (Pipeline batch 단위)
- Req-Resp Latency(ms): Pipeline 요청 패킷 전송 시점 → 첫 번째 응답 패킷 수신 시점 (네트워크 왕복 + Redis 3개 Command 일괄 처리)
- Total Transfer(ms): Pipeline 요청 패킷 전송 시점 → 마지막 응답 패킷(3개 RESP 응답 완료 시점) 수신 시점
- Payload Delivery(ms): Total Transfer - Req-Resp Latency (TCP 분할 응답 패킷의 물리적 도착 시간차)

■ 생성할 엑셀 파일: 6개 시트, 아래 순서대로

---

### 시트 1: Pipeline_Command_Detail
25개 Pipeline batch 각각의 상세 데이터.

헤더(1행):
| 지역구 | Keys | ReqTime(s) | Req-Resp Latency(ms) | Total Transfer(ms) | Payload Delivery(ms) | 응답패킷수 | 응답바이트 |

- Keys: 3개 Key를 쉼표로 연결한 문자열 (예: "idx:deposit:종로구, idx:monthlyRent:종로구:월세, idx:area:종로구:월세")
- 강남구 행은 노란색(FFF2CC) 배경 강조
- 25행 데이터 아래에 빈 행 2개 후 각주 영역 추가:

각주 1 - 용어 설명:
타이틀 행: "[용어 설명 (Glossary)]" (볼드 11pt)
다음 행: 헤더 [용어 | 영문 전체 표기 | 측정 구간 | 포함되는 비용] (배경색 548235, 흰색 볼드)
내용 5행 (배경색 E2EFDA):
  1) Req-Resp Latency(ms) | Request-Response Latency | Pipeline 요청 패킷 전송 → 첫 번째 응답 패킷 수신 | (1) 네트워크 편도 전파 Client→Server  (2) Redis 3개 Command 일괄 처리  (3) 네트워크 편도 전파 Server→Client  ※ Pipeline에서는 3 Command를 한 번의 왕복으로 처리하므로 Sequential 대비 RTT 2회분 절감
  2) Total Transfer(ms) | Total Transfer Time | Pipeline 요청 패킷 전송 → 마지막 응답 패킷 수신 (3개 RESP 응답 완료) | (1) Req-Resp Latency 전체  (2) 후속 응답 패킷 전송/수신 (TCP segmentation)  ※ 3개 Command의 응답 합산 데이터 볼륨에 비례 증가
  3) Payload Delivery(ms) | Payload Delivery Time | 첫 번째 응답 패킷 수신 → 마지막 응답 패킷 수신 | → Total Transfer − Req-Resp Latency  ※ 소량(수 패킷)이면 ~0ms, 대량(수천 패킷)이면 수 초
  4) 응답패킷수 | Response Packet Count | - | Pipeline 3개 Command 응답 전송에 사용된 TCP 패킷 수. MTU(~1460bytes) 초과 시 분할
  5) 응답바이트 | Response Bytes | - | TCP payload 총 바이트. 3개 RESP Array 응답의 합산 크기
  ※ 행 높이 45

각주 2 - Pipeline과 Sequential 비교:
타이틀 행: "[Pipeline vs Sequential 구조 비교]" (볼드 10pt)
내용 4행:
  - Sequential: Client→Server→Client를 Command마다 반복 (지역구당 RTT x 3)
  - Pipeline: 3개 Command를 한 패킷에 묶어 전송, 서버가 일괄 처리 후 응답 (지역구당 RTT x 1)
  - Pipeline Req-Resp Latency ≈ Sequential 단일 Req-Resp Latency (RTT는 동일, 서버 처리만 3배)
  - Pipeline 절감 대상 ← Sequential Req-Resp Latency x 2 (3회 왕복을 1회로 줄이면 2회분 제거)

⚠️ 주의: 셀 값에 = 기호를 사용하지 마라. Excel이 수식으로 해석하여 파일 복구 오류 발생.
⚠️ 주의: merge_cells 사용하지 마라. 동일 오류 원인.

컬럼 폭: A=14, B=75, C=22, D=22, E=22, F=22, G=14, H=14

---

### 시트 2: Packet_Dump
25개 Pipeline batch 각각에 대해 요청 패킷 1행 + 응답 패킷 N행의 raw 패킷 정보.

헤더(1행):
| Command Group | 패킷 역할 | Frame# | Time(s) | Source IP | Dest IP | SrcPort | DstPort | TCP Seq | TCP Ack | TCP Payload(bytes) | Frame Length(bytes) | Delta from Req(ms) |

각 Pipeline batch 그룹 구조:
1) 그룹 헤더 행 (배경 4472C4, 흰색 볼드):
   A열에 "[N/25] {지역구} | Pipeline 3 Cmds | RRL={값}ms TTx={값}ms"
2) 요청 행 (배경 DCE6F1, 볼드 9pt 색상 1F4E79):
   A="{지역구}", B="PIPELINE REQ →", C~L=패킷 정보, M=0.0
3) 첫 번째 응답 행 (배경 FFF2CC): B="FIRST RESP"
4) 중간 응답 행 (배경 FFFFFF): B="RESP [N/M]"
5) 마지막 응답 행 (배경 FCE4D6): B="LAST RESP (3 RESP Complete)"
   - 응답이 1개뿐이면 FIRST RESP만 표기
   - Delta from Req(ms) = (해당 응답 패킷 Time - 요청 패킷 Time) x 1000

컬럼 폭: A=22, B=26, C=9, D=16, E=16, F=16, G=9, H=9, I=13, J=13, K=18, L=18, M=18

---

### 시트 3: District_Summary
25개 지역구별 L2 측정값 집계.

헤더(1행, 행 높이 40, wrap_text):
| 지역구 | Req-Resp Latency(ms) | Total Transfer(ms) | Payload Delivery(ms) | 응답패킷수(개) | 응답바이트(bytes) | App PipelineLatency(ms) [L1] | App-패킷 차이(ms) [L1 - L2] | 오차율(%) |

- 강남구 행은 노란색(FFF2CC) 강조
- App PipelineLatency: 원본 xlsx Pipeline_Metrics 시트의 PipelineLatency(ms) 값
- 오차율 = (L1 PipelineLatency - L2 Total Transfer) / L1 PipelineLatency x 100

---

### 시트 4: Cross_Validation
Layer 1(App) vs Layer 2(패킷) 교차 검증.

헤더(1행, 행 높이 40, wrap_text):
| 지역구 | App MethodTime(ms) [L1] | App PipelineLatency(ms) [L1] | App NonIO(ms) [L1] | 패킷 Req-Resp Latency(ms) [L2] | 패킷 Total Transfer(ms) [L2] | 패킷 Payload Delivery(ms) [L2] | App-패킷 차이(ms) [L1 PipeLat - L2 TotalTx] | 오차율(%) | 순수 네트워크 비중(%) [L2 RRL / L1 PipeLat] | Java측 오버헤드(ms) [L1 PipeLat - L2 RRL] | Result 건수(합계) [R1+R2+R3] |

- 강남구 행 노란색 강조
- 순수 네트워크 비중 = L2 Req-Resp Latency / L1 PipelineLatency x 100
- Java측 오버헤드 = L1 PipelineLatency - L2 Req-Resp Latency
- Result 건수 = Result1_Count + Result2_Count + Result3_Count

---

### 시트 5: Statistics
전체 요약 통계. 4열 구조: [구분 | 지표 | 값 | 단위]

내용 (빈 행으로 섹션 구분):

섹션 1 - 캡처 환경:
  - Redis 서버 | 61.75.54.208:6379
  - 클라이언트 | 172.20.10.2
  - 전송 모드 | Pipeline (3 Commands/batch)
  - Pipeline batch 수 | 25
  - ZRANGEBYSCORE 총 수 | 75 (25 x 3)
  - 총 응답 패킷 수 | (계산값)
  - 총 응답 바이트 | (계산값)

섹션 2 - Req-Resp Latency (전체 25개):
  - 평균, 중앙값, 최소, 최대 | ms

섹션 3 - Req-Resp Latency (강남구 제외 24개):
  - 평균, 중앙값, 최소, 최대 | ms

섹션 4 - Total Transfer (강남구 제외 24개):
  - 평균, 중앙값 | ms

섹션 5 - 교차검증 (강남구 제외 24개):
  - L1 총 PipelineLatency | ms
  - L2 총 Total Transfer | ms
  - L2 총 Req-Resp Latency | ms
  - L1-L2 평균 차이 | ms
  - 평균 오차율 | %
  - 평균 네트워크 비중 | %

섹션 6 - Pipeline 효과 (Phase 1 Sequential 대비):
  - Before RTT 횟수 | 75 (25 x 3 Sequential)
  - After RTT 횟수 | 25 (25 x 1 Pipeline)
  - RTT 감소율 | 66.7%
  - 단일 Req-Resp Latency 추정값 (강남구 제외) | ms
  - Loop 전체 시간 [L1 LoopTime] | ms
  - 강남구 비중 [L1 강남 MethodTime / LoopTime] | %

구분 셀이 있는 행: 배경 D9E2F3, 볼드

---

### 시트 6: Gangnam_Analysis
강남구 단독 상세 분석.

헤더:
| 지표 | Req-Resp Latency(ms) | Total Transfer(ms) | Payload Delivery(ms) | 응답패킷수 | 응답바이트(KB) | L1 PipelineLatency(ms) | Result 건수 |

- 1행: 강남구 Pipeline batch 측정값 (배경 FFF2CC)
- 빈 행 후 개별 RESP 응답 크기 행 (3행):
  헤더: [RESP 응답 | 대상 Command | 응답 바이트(bytes) | Result Count [L1]]
  1) Response 1 | deposit(보증금) | (RESP 파싱값) | 16,008
  2) Response 2 | monthlyRent(월세) | (RESP 파싱값) | 36,251
  3) Response 3 | area(평수) | (RESP 파싱값) | 51,211
  합계 행 (배경 D9E2F3, 볼드)

- 빈 행 후 분석 텍스트 3행:
  - "Req-Resp Latency(47.8ms)는 다른 지역구 평균(38.9ms)과 유사한 수준. Pipeline 일괄 처리에 의한 RTT는 정상 범위."
  - "Total Transfer(2,791.7ms)가 극단적으로 높은 이유는 103,470건(4.2MB) 응답 데이터의 Payload Delivery Time(2,743.8ms)."
  - "강남구 MethodTime(2,894.5ms)은 전체 LoopTime(4,320.0ms)의 67.0%. 병목은 네트워크 왕복이 아닌 payload 볼륨이며, Pipeline으로는 해결 불가. 응답 데이터 압축 또는 페이지네이션이 필요."

---

■ 공통 스타일
- 폰트: Arial
- 헤더: 배경 2F5496, 흰색 볼드 10pt, 가운데 정렬
- 데이터: 10pt, 테두리 thin
- 강남구 강조: 배경 FFF2CC
- 소수점: ms 값은 3자리, 비율(%)은 1~2자리, 바이트는 천 단위 쉼표

■ 금지 사항
- merge_cells 사용 금지
- 셀 값에 = 기호로 시작하는 텍스트 금지 (← 또는 → 로 대체)
- Excel 수식(SUM, AVERAGE 등) 사용하지 말고 Python에서 계산한 값을 직접 기입
```

---

## 사용 방법

1. 새 세션을 열고 위 프롬프트를 복사/붙여넣기
2. 함께 첨부할 파일:
   - `WhireShark_Dump_Test_Phase2_OriginFile.pcapng`
   - `Application_Log_Test_Phase2_result.xlsx`
3. 생성된 xlsx를 현재 파일과 대조하여 검증

---

## Phase 1 지시문 대비 변경점 요약

| 항목 | Phase 1 (Sequential) | Phase 2 (Pipeline) |
|------|---------------------|--------------------|
| 요청 단위 | Command 개별 (75행) | Pipeline batch (25행) |
| 요청 패킷 구조 | 1 패킷 = 1 ZRANGEBYSCORE | 1 패킷 = 3 ZRANGEBYSCORE |
| 응답 경계 식별 | 다음 요청 패킷 시점으로 절단 | RESP 프로토콜 파싱으로 3개 응답 완료 시점 감지 |
| L1 대응 컬럼 | CmdLatency, Cmd1~Cmd3 | PipelineLatency, MethodTime, NonIO |
| Command_Detail | 75행 (Cmd1/Cmd2/Cmd3 구분) | 25행 (Pipeline batch 단위, Keys 열에 3개 Key 나열) |
| Packet_Dump 그룹 | 75개 그룹 | 25개 그룹 |
| District_Summary | 지역구당 3 Command 합산 | 지역구당 1 Pipeline batch |
| Cross_Validation | CmdLatency 기준 | PipelineLatency 기준, NonIO/Result건수 추가 |
| Gangnam_Analysis | 반환건수(Cmd별) | RESP 응답별 바이트 크기 + Result Count |
| 마지막 요청 절단 | gap 0.2초 기반 | RESP 파싱 (gap 기반보다 정확) |

---

## 기술 참고: RESP 프로토콜 파싱 기반 응답 경계 식별

Phase 2에서는 하나의 요청 패킷에 3개 Command가 묶여 있으므로, "다음 요청 패킷 시점" 기반 절단이 1~24번째 요청에는 유효하지만, 25번째(마지막) 요청에서는 후속 트래픽(Lettuce heartbeat 등)을 분리할 수 없다. 또한 gap 기반 절단은 네트워크 jitter에 따라 부정확할 수 있다.

RESP 파싱 방식은 TCP payload를 순서대로 연결하면서, 재귀적으로 RESP 타입별 완전성을 검증한다:
- *N: Array. N개의 하위 요소가 모두 완전해야 완료
- $N: Bulk String. N바이트 + CRLF가 존재해야 완료
- +, -, :: CRLF까지 존재해야 완료

Pipeline 응답은 3개의 최상위 Array가 연속으로 오므로, 3개가 모두 완전 파싱되는 시점이 해당 Pipeline batch의 응답 끝이다. 이 방식은 패킷 순서와 TCP reassembly만 정확하면 100% 정밀한 경계를 보장한다.

실측 검증: 강동구(25번째, 마지막 요청)에서 gap 기반(0.2초)은 5,372ms / 7.2MB를 반환했으나, RESP 파싱은 78.586ms / 68,242 bytes를 반환했다. App 측 PipelineLatency(80.11ms)와 RESP 파싱 결과의 오차는 1.9%로, gap 기반 대비 압도적으로 정확하다.
