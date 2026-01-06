# P6Spy → Oracle SQL 변환기 사용 매뉴얼

## 개요

P6Spy가 캡처한 CSV 로그 파일을 Oracle SQL*Plus/SQL Developer에서 직접 실행 가능한 형태로 변환하는 Python 스크립트.

## 요구사항

- Python 3.6 이상
- 외부 라이브러리 불필요 (표준 라이브러리만 사용)

## 기본 사용법

```bash
python p6spy_to_oracle_sql.py <입력_CSV_파일>
```

## 출력 파일

| 파일 | 설명 |
|------|------|
| `*_oracle_queries.csv` | 메타데이터 포함 분석용 CSV |
| `*_oracle_queries.sql` | SQL*Plus 즉시 실행 가능 스크립트 |

## 주요 옵션

| 옵션 | 설명 |
|------|------|
| `-o`, `--output-dir` | 출력 디렉토리 지정 |
| `-d`, `--no-duplicates` | 중복 SQL 제거 (executed_sql 기준) |
| `--dedupe-by-prepared` | prepared_sql 기준 중복 제거 |
| `--no-timing` | SET TIMING ON 제외 |
| `--no-comments` | SQL 앞 메타데이터 주석 제외 |
| `--csv-only` | CSV만 생성 |
| `--sql-only` | SQL 파일만 생성 |

## 사용 예시

```bash
# 기본 실행
python p6spy_to_oracle_sql.py p6spy_log.csv

# 출력 디렉토리 지정
python p6spy_to_oracle_sql.py p6spy_log.csv -o ./output

# 중복 제거 (동일 SQL 1회만)
python p6spy_to_oracle_sql.py p6spy_log.csv --no-duplicates

# 쿼리 템플릿별 대표 SQL만 추출 (Hard Parse 분석용)
python p6spy_to_oracle_sql.py p6spy_log.csv -d --dedupe-by-prepared

# 깔끔한 SQL만 (주석/타이밍 없이)
python p6spy_to_oracle_sql.py p6spy_log.csv --no-timing --no-comments
```

## 출력 CSV 컬럼

| 컬럼 | 설명 |
|------|------|
| seq | 순번 |
| original_line | 원본 CSV 라인 번호 |
| timestamp | 실행 시각 |
| thread | Tomcat 스레드명 |
| execution_time_ms | P6Spy 측정 실행시간(ms) |
| sql_type | SELECT/INSERT/UPDATE/DELETE |
| table_name | 대상 테이블 |
| parameter_count | 바인드 파라미터 수 |
| in_clause_count | IN절 파라미터 수 |
| oracle_sql | 실행 가능한 SQL (세미콜론 포함) |

## 실행 통계 출력 예시

```
============================================================
파싱 통계
============================================================
총 로그 행 수:      981
SQL 문장 수:        327
COMMIT 수:          654
기타 (SQL 없음):    0

[SQL 타입별 분포]
  SELECT         :   327

[테이블별 분포]
  REVIEW_STATISTICS             :   327
============================================================
```

## V$SQL 성능 분석 활용

생성된 SQL 파일 실행 후 아래 쿼리로 실행계획 및 파싱 비용 확인:

```sql
SELECT sql_id, plan_hash_value, 
       executions, parse_calls,
       elapsed_time/1000 as elapsed_ms,
       cpu_time/1000 as cpu_ms
FROM v$sql 
WHERE sql_text LIKE '%REVIEW_STATISTICS%'
ORDER BY elapsed_time DESC;
```

## 주의사항

- COMMIT/ROLLBACK 등 SQL이 없는 statement는 자동 제외
- 원본 CSV는 P6Spy 표준 포맷(16개 컬럼) 기준
- `--dedupe-by-prepared` 사용 시 같은 prepared statement 중 첫 번째만 추출
