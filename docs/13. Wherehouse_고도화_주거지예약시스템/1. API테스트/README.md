# Postman 데이터 셋 — Wherehouse 매물 방문 예약

설계 명세서 v1.4 의 **13 개 엔드포인트 + 로그인 헬퍼 1 개** 를 Postman 으로 바로 테스트할 수 있도록 정리한 데이터 셋이다.

## 파일

| 파일 | 용도 |
|------|------|
| `Wherehouse_VisitReservation.postman_collection.json` | 14 개 요청 묶음 (Collection) |
| `Wherehouse_VisitReservation.postman_environment.json` | 환경 변수 (baseUrl, 로그인 정보, 매물 ID 등) |

---

## 빠른 시작 — 5 분

### 1) 두 파일 모두 import

Postman 좌상단 **Import** → 위 2 개 JSON 파일 드래그.
좌측 상단의 환경 선택 드롭다운에서 **"Wherehouse VisitReservation Local"** 선택.

### 2) 환경 변수 채우기

좌측 사이드바 **Environments** → **"Wherehouse VisitReservation Local"** 클릭 → 아래 값을 본인 환경에 맞게 채운다.

| 변수 | 의미 | 예시 |
|------|------|------|
| `baseUrl` | 컨텍스트 경로 포함 | `http://localhost:8185/wherehouse` |
| `loginUserid` | 테스트 계정 ID | (본인 계정) |
| `loginPassword` | 테스트 계정 비밀번호 | (본인 비밀번호) |
| `propertyId` | 테스트 대상 매물의 32자 MD5 | `a1b2c3d4...` |
| `leaseType` | `CHARTER` 또는 `MONTHLY` | `CHARTER` |
| `windowStartTime` | 윈도우 시작 (미래 시각, KST 로컬) | `2026-06-15T10:00:00` |
| `windowEndTime` | 윈도우 종료 | `2026-06-15T13:00:00` |

다음 변수들은 **요청 응답에서 자동 추출**되므로 비워두면 된다:
- `windowId`, `slotId`, `slotIdAlt`, `reservationId`, `subscriptionId`, `notificationId`

> `slotIdAlt` 는 F001 응답의 2 번째 슬롯 ID. F005 취소 후 다른 슬롯으로 재예약 테스트 등에 쓸 수 있다.

### 3) 로그인

Collection 의 `00. 로그인 (헬퍼)` 실행 → 302 redirect 응답 + `Authorization` HttpOnly 쿠키가 Postman 쿠키 저장소에 자동 저장됨.

이후 모든 visit API 요청은 본 쿠키를 자동 첨부한다 — 별도 헤더 설정 불필요.

> Postman Settings → "Automatically follow redirects" 가 ON (기본값) 이어야 함.

### 4) 실행 순서 (한 사이클 예)

| 단계 | 요청 | 로그인 계정 | 비고 |
|------|------|------------|------|
| 1 | 00. 로그인 | **등록자** | 매물을 등록한 본인 계정 |
| 2 | F001 윈도우 공개 | 등록자 | 응답에서 `windowId`, `slotId` 자동 저장 |
| 3 | F008 등록자 슬롯 현황 | 등록자 | 슬롯이 보이는지 확인 |
| 4 | 00. 로그인 | **탐색자** | 다른 계정으로 재로그인 |
| 5 | F003 슬롯 조회 | 탐색자 (또는 비로그인) | 공개된 슬롯 확인 |
| 6 | F004 슬롯 예약 | 탐색자 | 응답에 등록자 연락 정보 확인 + `reservationId` 자동 저장 |
| 7 | F008 탐색자 예약 현황 | 탐색자 | 본인 예약 보임 |
| 8 | 00. 로그인 | 등록자 | 다시 등록자 |
| 9 | 알림 조회 | 등록자 | `SLOT_RESERVED` 알림 확인 + `notificationId` 자동 저장 |
| 10 | 알림 읽음 | 등록자 | 멱등 |
| 11 | 00. 로그인 | 탐색자 | 다시 탐색자 |
| 12 | F005 예약 취소 | 탐색자 | 슬롯이 AVAILABLE 로 복귀 |
| 13 | F001 → F004 → 시간 경과 → 스케줄러 1 분 후 → F007 결과 분류 | (혼합) | 종료 → 분류 흐름 |

---

## 시나리오별 상세

### A. 동시성 검증 (이중 예약)

03_constraints.sql 미적용 상태에서 **같은 `slotId` 에 F004 를 동시에 두 번** 호출하면, 두 요청 모두 201 응답을 받을 수 있다 (이중 예약 발생). 응용 코드가 동시성 제어를 두지 않았기 때문이며, 본인의 동시성 학습·측정 베이스라인이 된다.

Postman 의 Collection Runner 로 같은 요청을 동시 N 회 실행하거나, Runner > **"Run with iterations"** 로 측정하면 된다.

03 적용 후 다시 측정하면 `DataIntegrityViolationException` 이 발생하고 글로벌 핸들러가 자동으로 409 + E7002 로 변환해 응답한다 — 응용 코드 수정 불필요.

### B. 자기 매물 예약 (E7004)

등록자 계정으로 로그인 후 본인이 등록한 매물의 `slotId` 에 F004 호출 → **403 E7004** 응답 확인.

### C. 시간 겹침 (E7006)

탐색자 계정으로 매물 A 의 시간 10:00-10:30 슬롯 예약 → 매물 B 의 시간 10:00-10:30 슬롯 예약 시도 → **409 E7006** 응답 확인.

### D. 윈도우 철회 → 무효화 알림 (RESERVATION_INVALIDATED)

1. 등록자 F001 윈도우 공개
2. 탐색자 F004 예약 확정
3. 등록자 F002 윈도우 철회
4. 탐색자 알림 조회 → **RESERVATION_INVALIDATED** 알림 1 건 확인
5. 탐색자 F008 예약 현황 → 상태 `INVALIDATED` 확인

### E. 매물 비활성 연동 (PROPERTY_DEACTIVATED)

1. 등록자 F001 윈도우 공개 + 탐색자 F004 확정
2. 등록자가 `/api/v1/properties/{leaseType}/{propertyId}/status` (기존 매물 API) 로 매물 상태를 COMPLETED 또는 DELETED 로 전이
3. 탐색자 알림 조회 → **PROPERTY_DEACTIVATED** 알림 1 건 + 예약 상태 INVALIDATED

### F. 슬롯 종료 → 결과 분류 (F007)

1. F001 시 `windowStartTime` 을 **1-2 분 후** 로 설정 (예: 현재 시각 + 2 분)
2. F004 예약 확정
3. 슬롯 시작 시각 + 30 분 (슬롯 분할 단위) + 약 1 분 (스케줄러 주기) 대기
4. F008 탐색자 예약 현황 → 상태 COMPLETED
5. 등록자 로그인 후 F007 결과 분류 (VISITED 또는 NO_SHOW) → 200

### G. 재개방 알림 구독 → 재개방 (SLOT_REOPENED)

1. 탐색자 A 가 F004 로 슬롯 X 예약 확정
2. 탐색자 B 가 F006 으로 슬롯 X 구독 신청
3. 탐색자 A 가 F005 로 예약 취소
4. 탐색자 B 알림 조회 → **SLOT_REOPENED** 알림 1 건

---

## 응답 예시 (참고용)

### F001 윈도우 공개 — 201 성공

```json
{
  "window_id": 1,
  "property_id": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
  "lease_type": "CHARTER",
  "start_time": "2026-06-15T10:00:00",
  "end_time": "2026-06-15T13:00:00",
  "slot_duration_minutes": 30,
  "slots": [
    { "slot_id": 1, "start_time": "2026-06-15T10:00:00", "end_time": "2026-06-15T10:30:00", "status": "AVAILABLE" },
    { "slot_id": 2, "start_time": "2026-06-15T10:30:00", "end_time": "2026-06-15T11:00:00", "status": "AVAILABLE" }
  ],
  "created_at": "2026-06-10T14:30:00"
}
```

### F004 슬롯 예약 — 201 성공

```json
{
  "reservation_id": 1,
  "slot_id": 1,
  "property_id": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
  "lease_type": "CHARTER",
  "start_time": "2026-06-15T10:00:00",
  "end_time": "2026-06-15T10:30:00",
  "status": "CONFIRMED",
  "confirmed_at": "2026-06-10T15:30:00",
  "registrant": {
    "user_id": "owner123",
    "username": "김등록",
    "contact": "010-1234-5678"
  }
}
```

### F004 슬롯 예약 — 409 거부 (대체 슬롯 포함)

```json
{
  "error_code": "E7007",
  "message": "해당 슬롯은 이미 예약되었습니다.",
  "timestamp": "2026-06-10T15:30:01",
  "lease_type": "CHARTER",
  "available_slots": [
    { "slot_id": 3, "start_time": "2026-06-15T11:00:00", "end_time": "2026-06-15T11:30:00", "status": "AVAILABLE" }
  ]
}
```

### 일반 거부 응답 형식 (E7xxx)

```json
{
  "error_code": "E7005",
  "message": "같은 매물에 이미 활성 예약이 있습니다.",
  "timestamp": "2026-06-10T15:30:05"
}
```

---

## 자주 부딪히는 문제

| 증상 | 원인 / 해결 |
|------|------------|
| F001 호출에서 401 | 로그인 안 됨 → `00. 로그인` 먼저 |
| F001 호출에서 403 | 매물 등록자가 아님 (`propertyId` 가 본인이 등록한 매물인지 확인) |
| F001 호출에서 400 (E7001) | `windowStartTime` 이 과거 시각, 또는 (end - start) 가 30 분의 배수가 아님 |
| F004 호출에서 404 (E7202) | `slotId` 가 환경변수에 비어 있음 → F001 먼저 실행 |
| 로그인 후에도 401 | Postman Settings → Cookie 저장 활성화 확인, 또는 동일 도메인 (`localhost:8185`) 사용 확인 |
| 응답에 한글 깨짐 | Postman Settings → "Send no-cache header" 와 무관 — 서버가 UTF-8 기본 반환하므로 문제 없을 것 |

---

## 권장 검증 매트릭스

설계 명세서 8 개 기능별 최소 검증 케이스:

| 기능 | 정상 | 거부 (대표 1 케이스) |
|------|------|--------------------|
| F001 | 윈도우 공개 → 201 + 슬롯 목록 | 시간 겹침 → 409 E7011 |
| F002 | 활성 윈도우 철회 → 200 + 무효화 목록 | 이미 철회 → 409 E7002 |
| F003 | 슬롯 조회 → 200 | 매물 없음 → 404 E7204 |
| F004 | 예약 확정 → 201 + 등록자 연락 | 자기 매물 → 403 E7004 |
| F005 | 본인 예약 취소 → 200 + 슬롯 AVAILABLE | 시작 시각 경과 → 409 E7009 |
| F006 | RESERVED 슬롯 구독 → 201 | 이미 구독 → 409 E7012 |
| F007 | 종료 예약 분류 → 200 | 미종료 → 409 E7010 |
| F008 | 본인 자원 3 종 조회 → 200 | (본인 자원 조회는 비즈니스 거부 없음) |

위 시나리오 모두 통과되면 1 차 비즈니스 로직은 완전히 검증된 것이며, 이후 동시성 부하 시험으로 넘어가도 안전하다.

---

## 변경 이력

| 일자 | 내용 |
|------|------|
| 2026-05-21 | 최초 작성. Collection (14 요청) + Environment + README. 응답 변수 자동 추출 스크립트 포함 |
