# Wireshark Layer 2 분석 엑셀 재생성 지시문

> 이 문서와 함께 **pcapng 파일**과 **Application_Log_Test_Phase1_result.xlsx** (L1 원본)을 첨부하여 새 세션에서 지시하라.

---

## 지시 프롬프트 (아래를 복사하여 사용)

```
첨부한 두 파일을 사용하여 Wireshark Layer 2 분석 엑셀 파일을 생성하라.

■ 입력 파일
1. pcapng 파일: Redis ZRANGEBYSCORE 75개 요청(25개 서울 지역구 × 3 Command)의 패킷 캡처
2. Application_Log_Test_Phase1_result.xlsx: Layer 1(Application Log) 측정 결과. "Sequential_Metrics" 시트에서 지역구별 MethodTime, CmdLatency, Cmd1~Cmd3, Cmd1_Count~Cmd3_Count 값 사용

■ 환경 정보
- Redis 서버: 원격 서버 (IP는 pcapng에서 확인, 포트 6379)
- 클라이언트: pcapng에서 확인 (Redis 서버 방향으로 요청을 보내는 쪽)
- 프로토콜: RESP (Redis Serialization Protocol)
- 요청 Command: ZRANGEBYSCORE, Key 패턴은 idx:{type}:{district} 형식
  - type: deposit(보증금), monthlyRent(월세), area(평수)
  - 각 지역구당 3개 Command 순서: deposit → monthlyRent → area

■ 패킷 파싱 방법
1. tshark로 tcp.port == 6379 && tcp.len > 0 필터 적용하여 데이터 패킷 추출
2. 요청 패킷(클라이언트→서버)에서 TCP payload를 RESP 프로토콜로 디코딩하여 ZRANGEBYSCORE 요청 75개 식별
3. 응답 패킷(서버→클라이언트)을 각 요청에 매핑:
   - 요청 N의 응답 = 요청 N 전송 이후 ~ 요청 N+1 전송 이전 사이의 서버→클라이언트 패킷
   - 마지막 요청(75번째)은 gap 기반 절단: 직전 응답 패킷과 0.2초 이상 간격이 발생하면 이후 패킷 제외 (Lettuce heartbeat 등 후속 트래픽 분리)

■ 측정 지표 정의 (용어 통일 필수)
- Req-Resp Latency(ms): 요청 패킷 전송 시점 → 첫 번째 응답 패킷 수신 시점 (네트워크 왕복 + Redis 서버 처리)
- Total Transfer(ms): 요청 패킷 전송 시점 → 마지막 응답 패킷 수신 시점
- 첫 응답~마지막 응답 도착 간격(ms): Total Transfer - Req-Resp Latency (TCP 분할 응답 패킷의 물리적 도착 시간차)

■ 생성할 엑셀 파일: 6개 시트, 아래 순서대로

---

### 시트 1: Command_Detail
75개 ZRANGEBYSCORE 각각의 상세 데이터.

헤더(1행):
| 지역구 | Command | Key | ReqTime(s) | Req-Resp Latency(ms) | Total Transfer(ms) | 첫 응답~마지막 응답 도착 간격(ms) | 응답패킷수 | 응답바이트 |

- Command 값: Cmd1(보증금), Cmd2(월세), Cmd3(평수)
- 강남구 3행은 노란색(FFF2CC) 배경 강조
- 75행 데이터 아래에 빈 행 2개 후 각주 영역 추가:

각주 1 - 용어 설명:
타이틀 행: "[용어 설명 (Glossary)]" (볼드 11pt)
다음 행: 헤더 [용어 | 영문 전체 표기 | 측정 구간 | 포함되는 비용] (배경색 548235, 흰색 볼드)
내용 5행 (배경색 E2EFDA):
  1) Req-Resp Latency(ms) | Request-Response Latency | 요청 패킷 전송 → 첫 번째 응답 패킷 수신 | (1) 네트워크 편도 전파 Client→Server  (2) Redis Command 처리  (3) 네트워크 편도 전파 Server→Client  ※ 응답 크기와 무관하게 거의 일정
  2) Total Transfer(ms) | Total Transfer Time | 요청 패킷 전송 → 마지막 응답 패킷 수신 | (1) Req-Resp Latency 전체  (2) 후속 응답 패킷 전송/수신 (TCP segmentation)  ※ 데이터 볼륨에 비례 증가
  3) Payload Delivery(ms) | Payload Delivery Time | 첫 번째 응답 패킷 수신 → 마지막 응답 패킷 수신 | → Total Transfer − Req-Resp Latency  ※ 소량(1~3패킷)이면 ~0ms, 대량(수백패킷)이면 수 초
  4) 응답패킷수 | Response Packet Count | - | Redis 응답 전송에 사용된 TCP 패킷 수. MTU(~1460bytes) 초과 시 분할
  5) 응답바이트 | Response Bytes | - | TCP payload 총 바이트. RESP 프로토콜 오버헤드 + ZSet member 목록
  ※ 행 높이 45

각주 2 - 관계식:
타이틀 행: "[관계식]" (볼드 10pt)
내용 3행:
  - Total Transfer ← Req-Resp Latency + Payload Delivery | 전체 시간 ← 네트워크 왕복 + 데이터 수신
  - Pipeline 절감 대상 ← Req-Resp Latency x 2 | 3회 왕복을 1회로 줄이면 2회분 제거
  - Payload Delivery는 Pipeline과 무관 | 동일 데이터를 수신하므로 전송 시간 불변

⚠️ 주의: 셀 값에 = 기호를 사용하지 마라. Excel이 수식으로 해석하여 파일 복구 오류 발생.
⚠️ 주의: merge_cells 사용하지 마라. 동일 오류 원인.

컬럼 폭: A=25, B=28, C=48, D=55, E~I=18

---

### 시트 2: Packet_Dump
75개 Command 각각에 대해 요청 패킷 1행 + 응답 패킷 N행의 raw 패킷 정보.

헤더(1행):
| Command Group | 패킷 역할 | Frame# | Time(s) | Source IP | Dest IP | SrcPort | DstPort | TCP Seq | TCP Ack | TCP Payload(bytes) | Frame Length(bytes) | Delta from Req(ms) |

각 Command 그룹 구조:
1) 그룹 헤더 행 (배경 4472C4, 흰색 볼드):
   A열에 "[N/75] {지역구} - {Cmd} | {ZRANGEBYSCORE 전체 명령} | RRL={값}ms TTT={값}ms"
2) 요청 행 (배경 DCE6F1, 볼드 9pt 색상 1F4E79):
   A="지역구/Cmd", B="REQUEST →", C~L=패킷 정보, M=0.0
3) 첫 번째 응답 행 (배경 FFF2CC): B="FIRST RESP"
4) 중간 응답 행 (배경 FFFFFF): B="RESP [N/M]"
5) 마지막 응답 행 (배경 FCE4D6): B="LAST RESP"
   - 응답이 1개뿐이면 FIRST RESP만 표기
   - Delta from Req(ms) = (해당 응답 패킷 Time - 요청 패킷 Time) × 1000

컬럼 폭: A=22, B=16, C=9, D=16, E=16, F=16, G=9, H=9, I=13, J=13, K=18, L=18, M=18

---

### 시트 3: District_Summary
25개 지역구별 L2 측정값 집계.

헤더(1행, 행 높이 40, wrap_text):
| 지역구 | 네트워크 왕복 합계(ms) [Req-Resp Latency × 3] | 전체 전송 완료 합계(ms) [Total Transfer × 3] | 첫~마지막 응답 도착 간격 합계(ms) | 응답 패킷 수(개) | 응답 바이트(bytes) | App 측정값(ms) [L1 CmdLatency] | App-패킷 차이(ms) [L1 - L2] | 오차율(%) |

- 강남구 행은 노란색(FFF2CC) 강조
- L1 CmdLatency: 원본 xlsx Sequential_Metrics 시트의 CmdLatency(ms) 값
- 오차율 = (L1 CmdLatency - L2 Total Transfer 합) / L1 CmdLatency × 100

---

### 시트 4: Cross_Validation
Layer 1(App) vs Layer 2(패킷) 교차 검증.

헤더(1행, 행 높이 40, wrap_text):
| 지역구 | App 메서드 전체(ms) [L1 MethodTime] | App Redis 합계(ms) [L1 CmdLatency] | App Cmd1 보증금(ms) | App Cmd2 월세(ms) | App Cmd3 평수(ms) | 패킷 왕복 합계(ms) [L2 Req-Resp] | 패킷 전송완료 합계(ms) [L2 Total Transfer] | App-패킷 차이(ms) [L1 - L2] | 오차율(%) | 순수 네트워크 비중(%) [L2 Req-Resp / L1] | Java측 오버헤드(ms) [L1 - L2 Req-Resp] |

- 강남구 행 노란색 강조
- 순수 네트워크 비중 = L2 Req-Resp 합 / L1 CmdLatency × 100
- Java측 오버헤드 = L1 CmdLatency - L2 Req-Resp 합

---

### 시트 5: Statistics
전체 요약 통계. 4열 구조: [구분 | 지표 | 값 | 단위]

내용 (빈 행으로 섹션 구분):
- 캡처 환경: Redis 서버, 클라이언트, ZRANGEBYSCORE 수(75), 총 응답 패킷 수, 총 응답 바이트
- Req-Resp Latency (전체 75개): 평균, 중앙값, 최소, 최대
- Req-Resp Latency (강남구 제외 72개): 평균, 중앙값, 최소, 최대
- Total Transfer (강남구 제외 72개): 평균, 중앙값
- Command별 Req-Resp (강남구 제외): Cmd1 평균, Cmd2 평균, Cmd3 평균
- 교차검증 (강남구 제외 24개): L1 총 CmdLatency, L2 총 Total Transfer, L2 총 Req-Resp Latency, L1-L2 평균차이, 평균 오차율, 평균 네트워크 비중
- Pipeline 예상 효과: Before RTT 횟수(75), After RTT 횟수(25), RTT 감소율(66.7%), 단일 Req-Resp Latency 추정값, 지역구당 절감, 전체 절감

구분 셀이 있는 행: 배경 D9E2F3, 볼드

---

### 시트 6: Gangnam_Analysis
강남구 단독 상세 분석.

헤더:
| Command | Key | Req-Resp Latency(ms) | Total Transfer(ms) | 첫 응답~마지막 응답 도착 간격(ms) | 응답패킷수 | 응답바이트(KB) | L1 CmdLatency(ms) | 반환건수 |

- 3개 Command 행 (배경 FFF2CC)
- 빈 행 후 합계 행 (배경 D9E2F3, 볼드)
- 빈 행 후 분석 텍스트 2행:
  - "Req-Resp Latency는 다른 지역구와 동일(~38ms). Total Transfer가 극단적으로 높은 이유는 4.1MB Payload Delivery Time."
  - "Pipeline 적용 시 RTT 절감(~76ms)은 총 MethodTime 3,428ms 대비 2.2%. 강남구 병목은 네트워크 왕복이 아닌 payload 크기."
- L1 CmdLatency, 반환건수: L1 원본 xlsx에서 Cmd1(ms)~Cmd3(ms), Cmd1_Count~Cmd3_Count

---

■ 공통 스타일
- 폰트: Arial
- 헤더: 배경 2F5496, 흰색 볼드 10pt, 가운데 정렬
- 데이터: 10pt, 테두리 thin
- 강남구 강조: 배경 FFF2CC
- 소수점: ms 값은 3자리, 비율(%)은 1~2자리

■ 금지 사항
- merge_cells 사용 금지
- 셀 값에 = 기호로 시작하는 텍스트 금지 (← 또는 → 로 대체)
- Excel 수식(SUM, AVERAGE 등) 사용하지 말고 Python에서 계산한 값을 직접 기입
```

---

## 사용 방법

1. 새 세션을 열고 위 프롬프트를 복사/붙여넣기
2. 함께 첨부할 파일:
   - `WhireShark_Dump_Test_Phase1_OriginFile.pcapng`
   - `Application_Log_Test_Phase1_result.xlsx`
3. 생성된 xlsx를 현재 파일과 대조하여 검증
