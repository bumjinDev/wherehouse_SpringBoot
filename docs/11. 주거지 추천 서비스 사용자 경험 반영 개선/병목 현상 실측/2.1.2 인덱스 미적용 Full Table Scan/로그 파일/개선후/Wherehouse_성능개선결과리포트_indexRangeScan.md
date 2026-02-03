# Wherehouse

## 리뷰 검색 API 성능 개선 결과 리포트

**LIKE 패턴 최적화를 통한 Full Table Scan → Index Range Scan 전환**

- **분석 일자:** 2025-12-17
- **테스트 환경:** JMeter 5.6.3 (50 Threads, 1 Loop)

---

## 1. Executive Summary

`findPropertyIdsByName` 쿼리의 LIKE 패턴을 `'%keyword%'`에서 `'keyword%'`로 변경하고 `apt_nm` 컬럼에 인덱스를 생성하여 Full Table Scan을 Index Range Scan으로 전환했습니다. 그 결과 쿼리 소요시간이 **77.2% 감소**하고, 100ms 이상 슬로우 쿼리가 **103건에서 0건**으로 완전히 제거되었습니다.

### 1.1 핵심 성과 지표

| 항목 | 개선 전 | 개선 후 | 개선율 |
|---|---|---|---|
| 쿼리 소요시간 (평균) | 26.3ms | 6.0ms | **77.2% ↓** |
| 쿼리 소요시간 (최대) | 49ms | 24ms | **51.0% ↓** |
| 전체 메서드 소요시간 (평균) | 45.7ms | 22.4ms | **51.0% ↓** |
| 병목 비중 (평균) | 66.3% | 22.0% | **66.8% ↓** |
| 병목 비중 (최대) | 86.5% | 42.9% | **50.4% ↓** |
| 총 SQL 소요시간 | 2,589ms | 645ms | **75.1% ↓** |
| 슬로우 쿼리 (≥100ms) | 103건 | 0건 | **100% ↓** |

---

## 2. 변경 사항

### 2.1 Native Query 수정

| 개선 전 | 개선 후 |
|---|---|
| `LIKE '%' \|\| :name \|\| '%'` | `LIKE :name \|\| '%'` |

**ReviewRepository.java - findPropertyIdsByName 메소드:**

```sql
-- 변경 전 (Full Table Scan)
WHERE apt_nm LIKE '%관악산 삼성산 주공 3단지%'

-- 변경 후 (Index Range Scan)
WHERE apt_nm LIKE '관악산 삼성산 주공 3단지' || '%'
```

### 2.2 인덱스 생성

```sql
CREATE INDEX IDX_CHARTER_APT_NM ON PROPERTIES_CHARTER(APT_NM);
CREATE INDEX IDX_MONTHLY_APT_NM ON PROPERTIES_MONTHLY(APT_NM);
```

---

## 3. 상세 비교 분석

### 3.1 쿼리 소요 시간 비교 (findPropertyIdsByName)

| 통계 | 개선 전 | 개선 후 | 변화 |
|---|---|---|---|
| 최소 | 18ms | 1ms | **-17ms** |
| 최대 | 49ms | 24ms | **-25ms** |
| 평균 | 26.3ms | 6.0ms | **-20.3ms** |
| 중앙값 | 25.0ms | 2.0ms | **-23.0ms** |
| 표준편차 | 6.9ms | 9.1ms | +2.2ms |

### 3.2 전체 메서드 소요 시간 비교 (getReviews)

| 통계 | 개선 전 | 개선 후 | 변화 |
|---|---|---|---|
| 최소 | 27ms | 5ms | **-22ms** |
| 최대 | 90ms | 84ms | **-6ms** |
| 평균 | 45.7ms | 22.4ms | **-23.3ms** |
| 중앙값 | 34.0ms | 7.0ms | **-27.0ms** |

### 3.3 테이블별 SQL 소요 시간 비교

| 테이블 | 개선 전 | 개선 후 | 개선율 |
|---|---|---|---|
| PROPERTIES_CHARTER | 2,322ms | 444ms | **80.9% ↓** |
| PROPERTIES_MONTHLY | 2,316ms | 437ms | **81.1% ↓** |
| REVIEWS | 218ms | 157ms | **28.0% ↓** |

---

## 4. 실행 계획 비교

| 항목 | 개선 전 | 개선 후 |
|---|---|---|
| OPERATION | TABLE ACCESS FULL | INDEX RANGE SCAN + TABLE ACCESS BY ROWID |
| OPTIONS | FULL | RANGE SCAN |
| Predicates | Filter Predicates (비효율) | Access Predicates (효율) |
| COST (예상) | 137 | 3~5 |

---

## 5. 결론

### 5.1 성과 요약

- 쿼리 소요시간 **77.2% 감소** (26.3ms → 6.0ms)
- 전체 API 응답시간 **51.0% 감소** (45.7ms → 22.4ms)
- 병목 비중 **66.8% 감소** (66.3% → 22.0%)
- 슬로우 쿼리 **100% 제거** (103건 → 0건)
- 총 SQL 처리 시간 **75.1% 감소** (2,589ms → 645ms)

### 5.2 트레이드오프

LIKE 패턴 변경으로 인해 검색 동작이 변경되었습니다:

| 개선 전 (부분 일치) | 개선 후 (전방 일치) |
|---|---|
| '삼성' → '래미안삼성' 검색됨 ✓ | '삼성' → '래미안삼성' 검색 안됨 ✗ |
| '삼성' → '삼성래미안' 검색됨 ✓ | '삼성' → '삼성래미안' 검색됨 ✓ |

비즈니스 요구사항에 따라 전방 일치 검색이 적합한 경우 현재 최적화가 유효합니다. 부분 일치가 필수인 경우 Oracle Text Index 또는 Elasticsearch 도입을 검토해야 합니다.

### 5.3 포트폴리오 활용 가치

본 성능 개선 사례는 다음과 같은 기술적 역량을 입증합니다:

1. **성능 병목 식별:** P6Spy 로그 파싱을 통한 정량적 분석
2. **근본 원인 분석:** Oracle Explain Plan을 통한 Full Table Scan 확인
3. **최적화 적용:** LIKE 패턴 변경 + 인덱스 생성
4. **효과 검증:** JMeter 부하 테스트를 통한 개선율 측정 (77.2% 성능 향상)

---

*--- End of Report ---*
