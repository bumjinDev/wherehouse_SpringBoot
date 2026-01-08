# Oracle SQL_ID 계산기 - 사용 매뉴얼 및 기술 문서

## 1. 사용 매뉴얼

### 1.1 개요

이 도구는 SQL 텍스트로부터 Oracle의 SQL_ID와 HASH_VALUE를 계산한다. 리터럴 SQL을 입력하면 자동으로 바인드 변수 형태로 변환하여 V$SQL에서 확인되는 것과 동일한 SQL_ID를 산출한다.

### 1.2 요구사항

- Python 3.6 이상
- 표준 라이브러리만 사용 (추가 설치 불필요)

### 1.3 기본 사용법

```
python oracle_sql_id_calculator.py [옵션] "SQL문장"
```

### 1.4 옵션

| 옵션 | 설명 |
|------|------|
| `-f`, `--file` | 파일에서 SQL 읽기 |
| `-r`, `--raw` | 리터럴을 바인드로 변환하지 않고 원본 그대로 계산 |
| `-h`, `--help` | 도움말 출력 |

### 1.5 사용 예시

**예시 1: 간단한 SQL**
```cmd
python oracle_sql_id_calculator.py "select * from dual"
```
출력:
```
SQL_ID:     a5ks9fhw2v9s1
HASH_VALUE: 942515969
```

**예시 2: 파일에서 읽기 (권장)**
```cmd
python oracle_sql_id_calculator.py -f query.sql
```
출력:
```
SQL_ID:     6hhc28tdcnka6
HASH_VALUE: 1523206470
BIND_COUNT: 276
변환됨:     리터럴 → 바인드 변수
원본 길이:  9854 chars
변환 길이:  1742 chars
```

**예시 3: 원본 그대로 계산 (변환 없이)**
```cmd
python oracle_sql_id_calculator.py -r "select * from t where id = 'abc'"
```

### 1.6 주의사항

1. **세미콜론 자동 제거**: SQL 끝의 `;`는 자동으로 제거된다. Oracle V$SQL에는 세미콜론이 포함되지 않기 때문이다.

2. **명령줄 길이 제한**: Windows CMD는 약 8191자 제한이 있으므로 긴 SQL은 반드시 `-f` 옵션으로 파일에서 읽어야 한다.

3. **인코딩**: SQL 파일은 UTF-8로 저장해야 한다.

4. **대소문자/공백 민감**: SQL_ID는 대소문자와 공백에 민감하다. `SELECT * FROM DUAL`과 `select * from dual`은 서로 다른 SQL_ID를 생성한다.

---

## 2. 기술 문서: Oracle SQL_ID 생성 알고리즘

### 2.1 알고리즘 개요

Oracle은 SQL 텍스트를 기반으로 SQL_ID를 생성한다. 이 과정은 다음 단계로 구성된다:

1. SQL 텍스트 + NULL 바이트(0x00)를 MD5 해시
2. MD5 다이제스트의 바이트 8-15를 특수한 방식으로 64비트 값으로 변환
3. 64비트 값을 커스텀 Base32로 인코딩하여 13자리 SQL_ID 생성

### 2.2 상세 알고리즘

#### 2.2.1 입력 준비

```
입력 = SQL텍스트(UTF-8) + 0x00
```

Oracle은 SQL 텍스트 끝에 NULL 종료 문자를 추가한 후 해시를 계산한다.

#### 2.2.2 MD5 해시 계산

표준 RFC 1321 MD5 알고리즘을 사용하여 128비트(16바이트) 다이제스트를 생성한다.

```
MD5 다이제스트 = MD5(입력)
                 [0-7: 상위 64비트] [8-15: 하위 64비트]
```

#### 2.2.3 64비트 해시값 추출 (핵심)

**이 단계가 가장 중요하다.** Oracle은 단순히 8바이트를 통째로 리틀 엔디안으로 읽지 않는다. 대신 각 4바이트 블록을 개별적으로 엔디안 변환한다:

```
MD5 다이제스트: [byte0][byte1]...[byte7] | [byte8][byte9][byte10][byte11] | [byte12][byte13][byte14][byte15]
                      상위 64비트 (미사용)              블록1                         블록2

1. 블록1 (바이트 8-11) → 리틀 엔디안 변환 → n1 (상위 32비트)
2. 블록2 (바이트 12-15) → 리틀 엔디안 변환 → n2 (하위 32비트)
3. 64비트 해시값 = (n1 × 2³²) + n2
```

**잘못된 방식 (주의)**:
```python
# 이렇게 하면 틀린 결과가 나온다
hash_64 = struct.unpack('<Q', md5_digest[8:16])[0]
```

**올바른 방식**:
```python
h1 = md5_hex[16:24]  # 바이트 8-11의 hex
h2 = md5_hex[24:32]  # 바이트 12-15의 hex

# 각 4바이트를 개별 엔디안 변환
hn1 = h1[6:8] + h1[4:6] + h1[2:4] + h1[0:2]
hn2 = h2[6:8] + h2[4:6] + h2[2:4] + h2[0:2]

n1 = int(hn1, 16)
n2 = int(hn2, 16)
hash_64 = n1 * 4294967296 + n2
```

#### 2.2.4 Base32 인코딩

Oracle은 표준 Base32가 아닌 커스텀 알파벳을 사용한다:

```
0123456789abcdfghjkmnpqrstuvwxyz
```

**제외된 문자**: `e`, `i`, `l`, `o`

이들은 숫자 `0`, `1`, `3`과 시각적으로 혼동될 수 있어 제외되었다. AWR 리포트나 터미널에서 SQL_ID를 식별할 때의 가독성을 위한 설계 결정이다.

**인코딩 과정**:
```python
sql_id = ""
temp = hash_64
for _ in range(13):
    sql_id = BASE32_ALPHABET[temp % 32] + sql_id
    temp = temp // 32
```

64비트를 5비트씩 분할하면 12.8개 청크가 나오므로, 13자리 SQL_ID가 생성된다.

### 2.3 HASH_VALUE와의 관계

V$SQL.HASH_VALUE는 64비트 해시값의 하위 32비트다:

```
HASH_VALUE = hash_64 & 0xFFFFFFFF
           = n2 (위 알고리즘의 블록2 값)
```

Library Cache의 해시 버킷 결정에 이 값이 사용된다.

### 2.4 바인드 변수와 SQL_ID

JDBC PreparedStatement를 통해 실행된 SQL은 Oracle에 바인드 변수 형태로 전달된다:

| 애플리케이션 로그 | V$SQL에 저장되는 형태 |
|------------------|----------------------|
| `WHERE ID IN ('a','b','c')` | `WHERE ID IN (:1 ,:2 ,:3 )` |

따라서 동일한 바인드 개수를 가진 쿼리는 값이 달라도 **동일한 SQL_ID**를 공유한다. 이것이 바인드 변수 사용의 핵심 이점이다.

**Oracle의 바인드 변수 포맷**:
```
:1 ,:2 ,:3 ,:4 ... ,:N 
```
- 각 바인드 변수 뒤에 공백
- 쉼표 후 바로 다음 바인드 변수
- 마지막 바인드 변수 뒤에 공백 후 닫는 괄호

---

## 3. 검증된 테스트 케이스

### 3.1 Tanel Poder의 참조 케이스

```
SQL: "select * from dual"
SQL_ID: a5ks9fhw2v9s1
HASH_VALUE: 942515969
MD5: 02fc540d4440adb27409cba201a72d38
```

### 3.2 실제 Oracle V$SQL 검증

```
SQL: select rs1_0.PROPERTY_ID,... where rs1_0.PROPERTY_ID in (:1 ,:2 ,:3 ... ,:276 )
SQL_ID: 6hhc28tdcnka6
HASH_VALUE: 1523206470
BIND_COUNT: 276
```

---

## 4. 참고 문헌 및 출처

### 4.1 공식 Oracle 문서

1. **Oracle Database Reference - V$SQL**
   - URL: https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-SQL.html
   - 내용: SQL_ID, HASH_VALUE, FULL_HASH_VALUE 컬럼 정의

2. **Oracle Database Reference - V$SQLAREA**
   - URL: https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/V-SQLAREA.html
   - 내용: SQL_ID의 역할과 Library Cache 내 식별자로서의 기능

3. **Oracle Database SQL Language Reference - DBMS_CRYPTO.HASH**
   - URL: https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_CRYPTO.html
   - 내용: Oracle 내부 MD5 해시 함수 사용법

### 4.2 기술 블로그 및 분석 자료

1. **Tanel Poder - "SQL_ID is just a fancy representation of hash value"**
   - URL: https://tanelpoder.com/2009/02/22/sql_id-is-just-a-fancy-representation-of-hash-value/
   - 내용: SQL_ID가 MD5 해시 기반임을 최초로 공개 분석
   - 핵심 인용: "Oracle takes last 32 bits of the MD5 hash and this will be the hash value"

2. **Tanel Poder - "Calculate SQL_ID and SQL_HASH_VALUE from SQL text"**
   - URL: https://tanelpoder.com/2010/03/31/calculate-sql_id-and-sql_hash_value-from-sql-text/
   - 내용: SQL_ID 계산 스크립트 및 알고리즘 설명

3. **Perumal.org - "Computing Oracle SQL_ID and HASH_VALUE"**
   - URL: https://www.perumal.org/computing-oracle-sql_id-and-hash_value/
   - 내용: Java 구현 예제 및 상세 알고리즘
   - 핵심 인용: "Oracle passes SQL text with null terminator to standard MD5 hash function"

4. **GitHub: jkstill/oracle-demos - SQL-Hashing.md**
   - URL: https://github.com/jkstill/oracle-demos/blob/master/sql-hash-value/SQL-Hashing.md
   - 내용: Bash/PL/SQL 구현 및 Base32 알파벳 분석
   - 핵심 인용: "Notably absent are the characters 'e', 'i', 'l' and 'o'"

### 4.3 RFC 표준

1. **RFC 1321 - The MD5 Message-Digest Algorithm**
   - URL: https://www.rfc-editor.org/rfc/rfc1321
   - 내용: Oracle이 사용하는 MD5 알고리즘의 공식 명세

---

## 5. 아키텍처적 의의

### 5.1 MD5 선택의 설계 근거

SQL_ID의 목적은 암호학적 보안이 아니라 **Library Cache 내 빠른 커서 식별**이다. MD5는 128비트 출력으로 충분한 충돌 저항성을 제공하면서도 SHA 계열보다 계산 비용이 낮다. Hard Parse가 발생할 때마다 이 해시를 계산해야 하므로 연산 효율성이 우선순위였다.

### 5.2 64비트 절단의 의미

Oracle은 128비트 MD5 중 하위 64비트만 사용한다. 64비트면 이론적으로 1.8×10¹⁹개의 고유 값을 표현할 수 있으므로, 단일 데이터베이스 인스턴스에서 실질적인 충돌 가능성은 무시할 수 있다.

### 5.3 Library Cache와 HASH_VALUE

Library Cache는 해시 테이블 구조로 구현되어 있으며, HASH_VALUE(64비트 해시의 하위 32비트)가 버킷 결정에 사용된다. SQL_ID로 쿼리할 때도 Oracle은 내부적으로 SQL_ID에서 HASH_VALUE를 추출하여 해시 룩업을 수행한다.

### 5.4 Hard Parse vs Soft Parse

| 구분 | 조건 | 비용 |
|------|------|------|
| Hard Parse | SQL_ID가 Library Cache에 없음 | 높음 (구문 분석, 최적화, 실행 계획 생성) |
| Soft Parse | SQL_ID가 Library Cache에 있음 | 낮음 (기존 커서 재사용) |

**바인드 변수 사용의 핵심**: 동일한 바인드 개수 → 동일한 SQL_ID → Soft Parse로 커서 재사용

---

## 6. IN절 1000개 제한과 SQL_ID

### 6.1 Hibernate의 OR 분해 방식

1500개 항목을 처리할 때:
```sql
SELECT * FROM T WHERE ID IN (:1 ,:2 ... ,:1000 ) OR ID IN (:1001 ... ,:1500 )
```
- 생성되는 SQL_ID: 1개
- Hard Parse: 최초 1회

### 6.2 애플리케이션 레벨 Chunking

```sql
-- 쿼리 1
SELECT * FROM T WHERE ID IN (:1 ,:2 ... ,:1000 )
-- 쿼리 2
SELECT * FROM T WHERE ID IN (:1 ,:2 ... ,:500 )
```
- 생성되는 SQL_ID: 2개 (바인드 개수가 다름)
- Hard Parse: 각 패턴 최초 1회씩

### 6.3 성능 비교 시 고려사항

| 항목 | OR 분해 | Chunking |
|------|---------|----------|
| SQL_ID 개수 | 1개 (고정 패턴당) | 청크 패턴 수만큼 |
| 실행 계획 | CONCATENATION | INLIST ITERATOR |
| 캐시 재사용성 | 동일 패턴에서만 | 동일 청크 크기에서 재사용 가능 |
| 네트워크 왕복 | 1회 | 청크 수만큼 |

---

## 7. 버전 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|----------|
| 1.0 | 2026-01-08 | 초기 버전 (잘못된 엔디안 처리) |
| 1.1 | 2026-01-08 | 엔디안 처리 수정 (4바이트 개별 변환) |
| 1.2 | 2026-01-08 | 리터럴 → 바인드 자동 변환 기능 추가 |
| 1.3 | 2026-01-08 | 세미콜론 자동 제거 기능 추가 |

---

## 8. 라이선스

이 도구는 자유롭게 사용, 수정, 배포할 수 있다.

---

*문서 작성일: 2026년 1월 8일*
*작성자: 정범진 (Wherehouse 프로젝트)*
