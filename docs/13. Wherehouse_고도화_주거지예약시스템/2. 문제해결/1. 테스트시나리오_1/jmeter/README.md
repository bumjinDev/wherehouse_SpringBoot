# F004 시나리오 1 — JMeter 부하 테스트

같은 슬롯 ID 에 N 개 동시 요청을 발생시켜, F004 슬롯 예약의 동시성 거동을 측정한다.
Synchronizing Timer 로 N 개 스레드가 모두 도달할 때까지 대기 후 동시 출발시킨다.

## 파일

| 파일 | 용도 |
|---|---|
| `F004_시나리오1.jmx` | JMeter 테스트 플랜 본체. 시나리오 T-1/T-2/T-3 공용. |
| `users_sample_T1.csv` | T-1 (동일 슬롯 N 개) 용 샘플 — 모든 행이 같은 slot_id |
| `users_sample_T2.csv` | T-2 (서로 다른 슬롯 N 개, 대조군) 용 샘플 — 각 행이 다른 slot_id |
| `users_sample_T3.csv` | T-3 (혼합 부하) 용 샘플 — 일부는 같은 slot_id, 일부는 다른 slot_id |

CSV 컬럼: `SLOT_ID, JWT_TOKEN, USER_ID` (헤더 행은 jmx 에서 무시 설정).
한 줄 = 한 스레드의 요청. CSV 줄 수가 동시 스레드 수가 된다.

## 사전 준비

1. **JWT 토큰 N 개**: 회원 시스템에서 user01 ~ userNN 으로 로그인 후, 응답 `Set-Cookie: Authorization=<토큰>` 의 토큰 값을 CSV 의 `JWT_TOKEN` 컬럼에 채워 넣는다.
   - 토큰 값에는 `Bearer` 같은 prefix 를 붙이지 않는다. JWT 본문(eyJ... 으로 시작) 만 그대로 사용.
2. **대상 슬롯**: 백엔드에서 측정 대상 슬롯이 `AVAILABLE` 상태인지 확인. 사용된 슬롯은 다음 회차 측정 전에 다시 비워 두어야 한다 (예약 취소 또는 새 슬롯 사용).
3. **백엔드 실행 중**: `localhost:8185/wherehouse`. 다른 포트면 `-JBASE_PORT=` 로 오버라이드.

## 인증 방식 (주의)

본 백엔드의 `JwtAuthProcessorFilter` 는 JWT 를 **쿠키에서만** 추출한다 (`Authorization=<token>` 쿠키). 즉 표준적인 `Authorization: Bearer <token>` HTTP 헤더는 인식하지 않는다. 따라서 본 jmx 는 HTTP Header Manager 에서 다음과 같이 Cookie 헤더로 토큰을 전달한다:

```
Cookie: Authorization=${JWT_TOKEN}
```

CSV 의 `JWT_TOKEN` 값에는 토큰 본문만 넣는다 (`Bearer ` 없이).

### [측정 임시] JWT 만료시간 연장

부하 테스트 동안 토큰이 만료되지 않도록 `JWTUtil.EXPIRATION_TIME` 을 24 시간 (86,400,000 ms) 으로 임시 변경해 둔 상태. 측정 종료 후 원래 값 (3600000 = 1 시간) 으로 복원해야 한다.

## 실행 — CLI (회차 측정에 권장)

```powershell
# T-1, N=5
jmeter -n -t F004_시나리오1.jmx -JTHREADS=5 -JUSERS_CSV=users_sample_T1.csv -JRESULT_JTL=result_T1_N5.jtl

# T-1, N=10
jmeter -n -t F004_시나리오1.jmx -JTHREADS=10 -JUSERS_CSV=users_T1_N10.csv -JRESULT_JTL=result_T1_N10.jtl

# T-2, N=5 (대조군)
jmeter -n -t F004_시나리오1.jmx -JTHREADS=5 -JUSERS_CSV=users_sample_T2.csv -JRESULT_JTL=result_T2_N5.jtl

# T-3, N=5 (혼합)
jmeter -n -t F004_시나리오1.jmx -JTHREADS=5 -JUSERS_CSV=users_sample_T3.csv -JRESULT_JTL=result_T3_N5.jtl
```

오버라이드 가능한 변수:
- `-JBASE_HOST` (기본 `localhost`)
- `-JBASE_PORT` (기본 `8185`)
- `-JBASE_PATH` (기본 `/wherehouse`)
- `-JTHREADS` (기본 `5`) — Synchronizing Timer 의 group size 와도 자동 연동
- `-JUSERS_CSV` (기본 `users.csv`)
- `-JRESULT_JTL` (기본 `result.jtl`)

## 실행 — GUI (디버깅·예비 측정용)

`jmeter F004_시나리오1.jmx` 로 JMeter GUI 를 띄운 후, User Defined Variables 에서 값 조정.
실행 전에 View Results Tree 와 Aggregate Report 가 켜져 있는지 확인.

GUI 실행은 정확한 응답 시간 측정에 부적합 (JMeter 공식 권장). 회차 측정은 반드시 CLI 로.

## 응답 코드 해석

- `201 Created` — 성공. 본인이 슬롯을 확정함.
- `409 Conflict` — 거부. `error_code` 에 따라 의미가 다름:
  - `E7007` 슬롯이 이미 예약됨
  - `E7008` 슬롯 상태가 예약 가능 아님
  - `E7013` 슬롯 시작 시각이 지남
  - `E7005` 같은 매물에 본인 활성 예약이 이미 있음
  - `E7006` 시간 겹침
  - `E7002` 데이터베이스 무결성 백스톱 발동 (현재 환경에서는 발생 가능성 낮음)

응답 본문이 `.jtl` 에 저장되므로 회차 종료 후 `error_code` 분포 확인 가능.

## 회차 사이 정리

1. 본 회차 측정이 성공한 경우 슬롯이 `RESERVED` 가 되어 있다. 다음 회차 같은 슬롯을 다시 쓰려면:
   - 사용자가 직접 예약 취소 (`DELETE /api/v1/visit/reservations/{id}`)
   - 또는 새 슬롯으로 측정 (윈도우 추가 공개)
2. 백엔드 로그 파일 (`wherehouse/log/wherehouse.log`) 도 회차 사이에 분리하는 것이 좋다 (`report.py` 가 회차별 분석할 수 있도록).

## 보완 가능한 항목 (현재 미포함)

- **JWT 자동 발급** — 현재는 사전에 발급된 토큰을 CSV 에 넣어 두는 방식. 회원 로그인 API 가 있으면 Setup Thread Group 에 로그인 단계를 추가해 자동화 가능.
- **DB 락 모니터링** — Oracle `V$LOCK`, `V$SESSION_WAIT` 의 폴링. JMeter 외부에서 별도 스크립트로.
- **CP0 측정** — 거부된 트랜잭션도 백엔드 측정 로그에 등장시키려면 `VisitReservationWriteService.createReservation` 의 `AVAILABLE` 검증 *전* 에 추가 측정 라인이 필요. 현재는 CP1 이전에 throw 된 요청은 백엔드 측정 표에 안 잡힌다 (JMeter 응답시간으로 갈음).

세 항목은 회차 결과를 보고 보완 여부를 결정.
