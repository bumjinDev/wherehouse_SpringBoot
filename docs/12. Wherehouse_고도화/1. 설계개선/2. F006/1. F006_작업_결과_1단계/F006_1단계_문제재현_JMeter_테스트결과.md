# F006 1단계 — 문제 재현 (JMeter 동시 등록 테스트) 결과

**테스트 일시:** 2026-04-30 18:19 ~ 18:33  
**테스트 도구:** Apache JMeter 5.6.3  
**테스트 대상:** `CharterPropertyWriteService.createProperty()` (Optional 중복 체크 비활성화 상태)  
**엔드포인트:** `POST /wherehouse/api/v1/properties/charter`

---

## 1. 테스트 목적

현재 코드의 TOCTOU(Time-Of-Check to Time-Of-Use) 취약점을 실증적으로 재현한다.

`findById()` → `save()` 사이에 동시성 제어가 없으므로, 동일 매물을 동시 등록할 때 여러 스레드가 모두 "없음"을 확인하고 INSERT로 진행하여 `DataIntegrityViolationException`이 발생하고, 이를 catch하지 않아 500 에러가 사용자에게 노출되는 문제를 확인한다.

---

## 2. 테스트 환경

| 항목 | 값 |
|------|-----|
| 서버 | 로컬 Spring Boot (port 8185) |
| DB | Oracle XE (localhost:1521, SID: xe, 스키마: SCOTT) |
| 캐시 | Redis (43.202.178.156:6379) |
| HikariCP | maximum-pool-size: 12, auto-commit: false |
| 인증 | JWT Cookie (userId: yhjj815) |
| Synchronizing Timer | 모든 스레드가 준비 완료 후 동시 발사 |

### 테스트 대상 코드 상태

`CharterPropertyWriteService.createProperty()` — Optional 중복 체크를 의도적으로 비활성화하여 TOCTOU 갭을 노출시킨 상태:

```java
/* 매물 ID 를 조회하여 전세 매물들 중 중복 매물 여부 확인. */

// 테스트 목적 Optional 처리 해제
charterRepository.findById(propertyId);

// 테스트 위한 원래 코드 일시적 해제
// Optional<PropertyCharterEntity> existing = charterRepository.findById(propertyId);
// if (existing.isPresent()) {
//     throw new DuplicatePropertyException("이미 등록된 전세 매물입니다. propertyId=" + propertyId);
// }
```

`findById()`를 호출하되 결과를 사용하지 않으므로, 모든 요청이 `save()`까지 도달한다. 이렇게 하면 Oracle PK 유니크 제약이 유일한 보호 장치가 되며, `DataIntegrityViolationException`이 catch되지 않아 500 에러로 노출되는 상황을 정확히 재현할 수 있다.

---

## 3. 시나리오별 결과

### S1 — 동시 2건 동일 매물

| 항목 | 값 |
|------|-----|
| 스레드 수 | 2 |
| 매물 데이터 | 테스트아파트_S1 (sggCd=11680, 지번=123-45, 15층, 84.99㎡) |
| 생성된 propertyId | `f767b74fab7e867c6f44a884779e72fe` (2건 동일) |
| 동시 진입 시각 | 18:19:48.970 |

**결과:**

| 스레드 | 응답 코드 | 상세 |
|--------|----------|------|
| exec-7 | **201 Created** | INSERT 성공, Redis 동기화 완료 |
| exec-1 | **500 Internal Server Error** | `DataIntegrityViolationException` (ORA-00001) 미처리 |

**서버 로그 핵심:**
```
18:19:48.972 [exec-7] INFO  매물 ID=f767b74fab7e867c6f44a884779e72fe
18:19:48.972 [exec-1] INFO  매물 ID=f767b74fab7e867c6f44a884779e72fe
18:19:48.977 [exec-1] ERROR ORA-00001: 무결성 제약 조건(SCOTT.PK_PROPERTIES_CHARTER)에 위배됩니다
```

두 스레드가 동일 시각(18:19:48.970)에 `createProperty()`에 진입하여 같은 propertyId를 산출했고, exec-7의 INSERT가 먼저 커밋된 후 exec-1의 INSERT가 PK 제약 위반으로 실패했다. `DataIntegrityViolationException`을 catch하는 코드가 없어 500으로 노출되었다.

**판정: 기대 결과(1건 201, 1건 500)와 일치 — TOCTOU 재현 성공**

---

### S2 — 동시 5건 동일 매물

| 항목 | 값 |
|------|-----|
| 스레드 수 | 5 |
| 매물 데이터 | 테스트아파트_S2 (sggCd=11680, 지번=456-78, 10층, 59.96㎡) |
| 생성된 propertyId | `69c669d0075a586d02587007292ced99` (5건 동일) |
| 동시 진입 시각 | 18:27:41.598 |

**결과:**

| 스레드 | 응답 코드 | 상세 |
|--------|----------|------|
| exec-8 | **201 Created** | INSERT 성공, Redis 동기화 완료 |
| exec-3 | **500 Internal Server Error** | ORA-00001 (18:27:41.612) |
| exec-4 | **500 Internal Server Error** | ORA-00001 (18:27:41.612) |
| exec-5 | **500 Internal Server Error** | ORA-00001 (18:27:41.612) |
| exec-6 | **500 Internal Server Error** | ORA-00001 (18:27:41.624) |

**서버 로그 핵심:**
```
18:27:41.608 [exec-4] INFO  매물 ID=69c669d0075a586d02587007292ced99
18:27:41.608 [exec-5] INFO  매물 ID=69c669d0075a586d02587007292ced99
18:27:41.609 [exec-8] INFO  매물 ID=69c669d0075a586d02587007292ced99
18:27:41.609 [exec-3] INFO  매물 ID=69c669d0075a586d02587007292ced99
18:27:41.609 [exec-6] INFO  매물 ID=69c669d0075a586d02587007292ced99

18:27:41.612 [exec-5] ERROR ORA-00001: 무결성 제약 조건(SCOTT.PK_PROPERTIES_CHARTER)에 위배됩니다
18:27:41.612 [exec-4] ERROR ORA-00001: 무결성 제약 조건(SCOTT.PK_PROPERTIES_CHARTER)에 위배됩니다
18:27:41.612 [exec-3] ERROR ORA-00001: 무결성 제약 조건(SCOTT.PK_PROPERTIES_CHARTER)에 위배됩니다
18:27:41.615 [exec-8] INFO  전세 매물 Redis 동기화(등록) 완료: propertyId=69c669d0075a586d02587007292ced99
18:27:41.624 [exec-6] ERROR ORA-00001: 무결성 제약 조건(SCOTT.PK_PROPERTIES_CHARTER)에 위배됩니다
```

5개 스레드 중 exec-8만 INSERT에 성공했고, 나머지 4개는 모두 PK 제약 위반으로 실패했다. exec-3, exec-4, exec-5는 거의 동시에(18:27:41.612) 실패했고, exec-6은 약간 뒤(18:27:41.624)에 실패했다. 이는 exec-6이 exec-8의 트랜잭션 커밋을 대기(block)하다가 커밋 완료 후 ORA-00001을 받은 것으로, Oracle PK 인덱스의 배타적 락 메커니즘이 동작하는 것을 간접적으로 확인할 수 있다.

**판정: 기대 결과(1건 201, 4건 500)와 일치 — 높은 동시성에서도 동일한 TOCTOU 문제 재현**

---

### S3 — 서로 다른 매물 동시 등록 (기준선)

| 항목 | 값 |
|------|-----|
| 스레드 수 | 3 |
| 매물 데이터 | CSV에서 스레드별로 다른 매물 로드 |
| 동시 진입 시각 | 18:33:19.695 |

**CSV 데이터:**

| 매물명 | sggCd | 지번 | 층 | 전용면적 |
|--------|-------|------|-----|---------|
| 테스트아파트_S3_A | 11680 | 700-1 | 5 | 59.96 |
| 테스트아파트_S3_B | 11650 | 800-2 | 12 | 84.99 |
| 테스트아파트_S3_C | 11710 | 900-3 | 8 | 74.52 |

**결과:**

| 스레드 | 매물 | propertyId | 응답 코드 |
|--------|------|------------|----------|
| exec-10 | 테스트아파트_S3_A | `078fcaeb28894e987a3349c56023da3c` | **201 Created** |
| exec-2 | 테스트아파트_S3_B | `109c9cd1a583ebf6133ace54c7b14e71` | **201 Created** |
| exec-9 | 테스트아파트_S3_C | `5db549e5743dbb150bb334ec94c099b2` | **201 Created** |

**서버 로그 핵심:**
```
18:33:19.703 [exec-10] INFO  전세 매물 등록 요청: aptNm=테스트아파트_S3_A, sggCd=11680
18:33:19.703 [exec-2]  INFO  전세 매물 등록 요청: aptNm=테스트아파트_S3_B, sggCd=11650
18:33:19.703 [exec-9]  INFO  전세 매물 등록 요청: aptNm=테스트아파트_S3_C, sggCd=11710

18:33:19.707 [exec-9]  INFO  매물 ID=5db549e5743dbb150bb334ec94c099b2
18:33:19.707 [exec-2]  INFO  매물 ID=109c9cd1a583ebf6133ace54c7b14e71
18:33:19.707 [exec-10] INFO  매물 ID=078fcaeb28894e987a3349c56023da3c

18:33:19.714 [exec-9]  INFO  전세 매물 Redis 동기화(등록) 완료
18:33:19.714 [exec-2]  INFO  전세 매물 Redis 동기화(등록) 완료
18:33:19.714 [exec-10] INFO  전세 매물 Redis 동기화(등록) 완료
```

3개 스레드가 동시에 진입했지만 각각 다른 propertyId가 산출되었으므로 PK 충돌이 발생하지 않았다. 모두 INSERT 성공, Redis 동기화 완료.

**판정: 기대 결과(3건 모두 201)와 일치 — 동시성 문제는 동일 PK 충돌에만 국한됨을 확인**

---

## 4. 종합 분석

### 4-1. 결과 요약

| 시나리오 | 기대 결과 | 실제 결과 | 판정 |
|----------|----------|----------|------|
| S1 (동시 2건 동일 매물) | 1건 201, 1건 500 | 1건 201, 1건 500 | **일치** |
| S2 (동시 5건 동일 매물) | 1건 201, 4건 500 | 1건 201, 4건 500 | **일치** |
| S3 (서로 다른 매물 동시 등록) | 3건 모두 201 | 3건 모두 201 | **일치** |

### 4-2. 확인된 문제

1. **TOCTOU 갭 존재**: `findById()` → `save()` 사이에 동시성 제어가 없으므로, 동일 매물에 대한 동시 요청이 모두 "없음"을 확인하고 INSERT로 진행한다.
2. **DataIntegrityViolationException 미처리**: Oracle PK 유니크 제약이 후착 INSERT를 거부하지만(`ORA-00001`), 이 예외를 catch하는 코드가 없어 Spring의 기본 에러 핸들러가 500 Internal Server Error를 반환한다.
3. **사용자 경험 불량**: 500 에러는 서버 내부 장애를 의미하므로, "동일 매물이 이미 등록됨"이라는 비즈니스적 의미를 사용자에게 전달하지 못한다.

### 4-3. Oracle PK 제약의 역할 (S2 로그에서 관찰)

S2에서 exec-6이 다른 3개 스레드(exec-3, 4, 5)보다 약 12ms 늦게 실패한 것은 주목할 만하다:
- exec-3, 4, 5: `18:27:41.612`에 ORA-00001 발생
- exec-6: `18:27:41.624`에 ORA-00001 발생

이는 exec-6이 exec-8의 INSERT에 의해 PK 인덱스 배타적 락에서 block되었다가, exec-8의 커밋 완료 후 ORA-00001을 수신한 것으로 추정된다. 이 관찰은 2단계(Oracle 락 메커니즘 학습)에서 실증적으로 확인할 예정이다.

### 4-4. 데이터 정합성

Oracle PK 유니크 제약 덕분에 **중복 INSERT는 발생하지 않았다** — 각 시나리오에서 정확히 1건만 테이블에 적재되었다. 문제는 "중복 데이터가 발생하는 것"이 아니라, "후착 요청에 대한 에러 응답이 500(서버 장애)으로 노출되는 것"이다.

---

## 5. 다음 단계

이 결과를 바탕으로 2단계(Oracle 락 메커니즘 학습)에서 SELECT 무락/INSERT 배타적 락 동작을 SQL 레벨에서 실증하고, 3단계에서 대안(A-1, A-2, B)을 각각 적용하여 동일 JMeter 테스트로 비교한다.
