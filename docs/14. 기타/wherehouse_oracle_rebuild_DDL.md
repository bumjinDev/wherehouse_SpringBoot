# Wherehouse 배포 Oracle 테이블 재구축 DDL 명세서

> 작성일: 2026-06-08
> 대상: `jdbc:oracle:thin:@61.75.54.208:1521:xe` · 스키마 **SCOTT** (계정 유지, 테이블만 재생성)
> 근거: 현재 `wherehouse` 프로젝트의 JPA 엔티티 + 권위 DDL 스크립트(코드 직접 분석)
> 범위: `PropertyManagement`, `recommand`, `VisitReservation`, `review` 패키지가 사용하는 테이블 (**채팅 서비스 제외**)

---

## 0. 전제 (DDL 작성 기준)

| 항목 | 내용 |
|---|---|
| 배포 대상 | `61.75.54.208:1521:xe`, 스키마 **SCOTT** (application.yml) |
| `ddl-auto` | **`none`** → Hibernate가 테이블을 자동 생성/검증하지 않음. **모든 테이블 수동 생성 필요**. 엔티티가 남아있어도 테이블이 없을 때 기동 실패는 없음 |
| 명명 전략 | `PhysicalNamingStrategyStandardImpl` → `@Table`/`@Column` 이름이 **대문자 그대로** 테이블/컬럼명 |
| 시간 타입 | 모든 `LocalDateTime` → Oracle `TIMESTAMP` (KST 로컬 저장, 타임존 없음) |

**review 패키지 주의** — 구버전 단일 테이블(`REVIEWS`, `REVIEW_KEYWORDS`, `REVIEW_STATISTICS`)을 쓰는 `Review`/`ReviewKeyword`/`ReviewStatistics` 엔티티·리포지토리는 **정의만 남고 어떤 서비스에도 주입되지 않은 死코드**다. 현재 살아있는 리뷰 기능(`ReviewWriteService`/`ReviewQueryService`/`PropertySearchService`)은 **전세/월세 분리 6개 테이블만** 사용한다. → 운영 필수는 분리 6종, 구버전 3종은 선택(§F).

### 생성 대상 인벤토리

| 패키지 | 테이블 | 시퀀스 |
|---|---|---|
| **PropertyManagement** | `PROPERTIES_CHARTER`, `PROPERTIES_MONTHLY`, `PROPERTY_SYNC_FAILURES` | (없음 — 앱 주입 PK / IDENTITY) |
| **recommand** | `ANALYSIS_CRIME_STATISTICS`, `ANALYSIS_POPULATION_DENSITY`, `ANALYSIS_ENTERTAINMENT_STATISTICS` ＋ 위 `PROPERTIES_*` 읽음 | `SEQ_ANALYSIS_CRIME_STATISTICS`, `SEQ_ANALYSIS_POPULATION_DENSITY`, `SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS` |
| **VisitReservation** | `VISIT_WINDOW`, `VISIT_SLOT`, `VISIT_RESERVATION`, `REOPEN_SUBSCRIPTION`, `VISIT_NOTIFICATION` | `SEQ_VISIT_WINDOW`, `SEQ_VISIT_SLOT`, `SEQ_VISIT_RESERVATION`, `SEQ_REOPEN_SUBSCRIPTION`, `SEQ_VISIT_NOTIFICATION` |
| **review** | `REVIEWS_CHARTER`, `REVIEWS_MONTHLY`, `REVIEW_KEYWORDS_CHARTER`, `REVIEW_KEYWORDS_MONTHLY`, `REVIEW_STATISTICS_CHARTER`, `REVIEW_STATISTICS_MONTHLY` | `SEQ_REVIEW_CHARTER_ID`, `SEQ_REVIEW_MONTHLY_ID`, `SEQ_KEYWORD_CHARTER_ID`, `SEQ_KEYWORD_MONTHLY_ID` |

> 합계 17개 테이블 + 12개 시퀀스 (구버전 review 3종·시퀀스 2종 제외 시).

---

## A. PropertyManagement

> PROPERTIES 두 테이블은 PropertyManagement(쓰기)·recommand(읽기)가 **공유**한다. 컬럼 타입은 현재 write-side 엔티티(`PropertyCharterEntity`/`PropertyMonthlyEntity`) 명세 기준. PK `PROPERTY_ID`는 엔티티가 `columnDefinition="CHAR(32)"`로 못박아 **CHAR(32)**(MD5 32자 고정).

```sql
-- ============================================================
-- A-1. PROPERTIES_CHARTER (전세 매물) — 22컬럼
-- ============================================================
CREATE TABLE PROPERTIES_CHARTER (
    PROPERTY_ID            CHAR(32)        NOT NULL,
    APT_NM                 VARCHAR2(100)   NOT NULL,   -- ⚠ 엔티티=100 / 2026-02 배포본=255
    EXCLU_USE_AR           NUMBER(10,4)    NOT NULL,
    FLOOR                  NUMBER(3,0)     NOT NULL,
    BUILD_YEAR             NUMBER(4,0),
    DEAL_DATE              VARCHAR2(10),
    DEPOSIT                NUMBER(15,0),
    LEASE_TYPE             VARCHAR2(10),                -- 한글 '전세' 저장(기존 관례)
    UMD_NM                 VARCHAR2(100),
    JIBUN                  VARCHAR2(50)    NOT NULL,   -- ⚠ 엔티티=50 / 배포본=255
    SGG_CD                 VARCHAR2(10)    NOT NULL,   -- ⚠ 엔티티=10 / 배포본=255
    ADDRESS                VARCHAR2(200),               -- ⚠ 엔티티=200 / 배포본=255
    AREA_IN_PYEONG         NUMBER(10,4),
    RGST_DATE              VARCHAR2(12),
    DISTRICT_NAME          VARCHAR2(50),
    LAST_UPDATED           TIMESTAMP,
    DATA_SOURCE            VARCHAR2(10)    DEFAULT 'BATCH'  NOT NULL,
    STATUS                 VARCHAR2(10)    DEFAULT 'ACTIVE' NOT NULL,
    REGISTERED_USER_ID     VARCHAR2(100),
    REGISTERED_AT          TIMESTAMP,
    MODIFIED_AT            TIMESTAMP,
    USER_PROPOSED_DEPOSIT  NUMBER(15,0),
    CONSTRAINT PK_PROPERTIES_CHARTER PRIMARY KEY (PROPERTY_ID)
);

-- ============================================================
-- A-2. PROPERTIES_MONTHLY (월세 매물) — 24컬럼
--      = Charter + MONTHLY_RENT + USER_PROPOSED_MONTHLY_RENT
-- ============================================================
CREATE TABLE PROPERTIES_MONTHLY (
    PROPERTY_ID                 CHAR(32)        NOT NULL,
    APT_NM                      VARCHAR2(100)   NOT NULL,   -- ⚠ 100 / 배포본 255
    EXCLU_USE_AR                NUMBER(10,4)    NOT NULL,
    FLOOR                       NUMBER(3,0)     NOT NULL,
    BUILD_YEAR                  NUMBER(4,0),
    DEAL_DATE                   VARCHAR2(10),
    DEPOSIT                     NUMBER(15,0),
    MONTHLY_RENT                NUMBER(10,0),
    LEASE_TYPE                  VARCHAR2(10),               -- 한글 '월세' 저장
    UMD_NM                      VARCHAR2(100),
    JIBUN                       VARCHAR2(50)    NOT NULL,   -- ⚠ 50 / 배포본 255
    SGG_CD                      VARCHAR2(10)    NOT NULL,   -- ⚠ 10 / 배포본 255
    ADDRESS                     VARCHAR2(200),               -- ⚠ 200 / 배포본 255
    AREA_IN_PYEONG              NUMBER(10,4),
    RGST_DATE                   VARCHAR2(12),
    DISTRICT_NAME               VARCHAR2(50),
    LAST_UPDATED                TIMESTAMP,
    DATA_SOURCE                 VARCHAR2(10)    DEFAULT 'BATCH'  NOT NULL,
    STATUS                      VARCHAR2(10)    DEFAULT 'ACTIVE' NOT NULL,
    REGISTERED_USER_ID          VARCHAR2(100),               -- 2026-04-23 Charter와 100으로 통일
    REGISTERED_AT               TIMESTAMP,
    MODIFIED_AT                 TIMESTAMP,
    USER_PROPOSED_DEPOSIT       NUMBER(15,0),
    USER_PROPOSED_MONTHLY_RENT  NUMBER(10,0),
    CONSTRAINT PK_PROPERTIES_MONTHLY PRIMARY KEY (PROPERTY_ID)
);

-- 매물 인덱스 (출처: ERD_MODIFIY.sql) ----------------------------
CREATE INDEX IDX_PROP_CHARTER_USER   ON PROPERTIES_CHARTER (REGISTERED_USER_ID);
CREATE INDEX IDX_PROP_CHARTER_STATUS ON PROPERTIES_CHARTER (STATUS, DATA_SOURCE);
CREATE INDEX IDX_PROP_MONTHLY_USER   ON PROPERTIES_MONTHLY (REGISTERED_USER_ID); -- 원본 스크립트 누락분(대칭상 추가 권장)
CREATE INDEX IDX_PROP_MONTHLY_STATUS ON PROPERTIES_MONTHLY (STATUS, DATA_SOURCE);

-- ============================================================
-- A-3. PROPERTY_SYNC_FAILURES (F008 Redis 동기화 실패 기록)
--      PK는 Oracle IDENTITY(GenerationType.IDENTITY)
-- ============================================================
CREATE TABLE PROPERTY_SYNC_FAILURES (
    FAILURE_ID      NUMBER GENERATED ALWAYS AS IDENTITY,
    PROPERTY_ID     VARCHAR2(32)  NOT NULL,     -- 엔티티 length=32, columnDefinition 없음 → VARCHAR2
    LEASE_TYPE      VARCHAR2(10)  NOT NULL,
    OPERATION_TYPE  VARCHAR2(20)  NOT NULL,
    FAIL_STEP       VARCHAR2(50)  NOT NULL,
    FAIL_REASON     VARCHAR2(500),
    FAIL_TIME       TIMESTAMP     DEFAULT SYSTIMESTAMP,
    RETRY_COUNT     NUMBER        DEFAULT 0,
    MAX_RETRIES     NUMBER        DEFAULT 5,
    RESOLVED        CHAR(1)       DEFAULT 'N',
    RESOLVED_TIME   TIMESTAMP,
    RESOLVED_METHOD VARCHAR2(20),
    CONSTRAINT PK_PROPERTY_SYNC_FAILURES PRIMARY KEY (FAILURE_ID)
);
```

> `DATA_SOURCE`/`STATUS`의 `CHECK IN(...)` 제약은 원래 배포본에 없었고(앱 `@Enumerated`로만 강제) 위에도 넣지 않았다. DB CHECK까지 원하면 §E 참고.

---

## B. recommand (안전성 분석 가공 데이터)

> recommand가 **실제로 읽는** 분석 테이블은 이 3개뿐(`AnalysisCrimeRepository`/`AnalysisPopulationDensityRepository`/`AnalysisEntertainmentRepository`). 나머지 15개 `ANALYSIS_*`는 별도 프로젝트(AnalyzeSafetyScore)용이라 범위 밖. recommand는 이 외에 `PROPERTIES_*`(§A)도 읽는다.
> 원본 `create_analysis_tables.sql`엔 PK가 없었으나 엔티티가 `@Id ID`를 선언하므로 **PK(ID) 추가** 권장(아래 반영).

```sql
-- ============================================================
-- B-1. ANALYSIS_CRIME_STATISTICS (자치구별 5대범죄 통계)
-- ============================================================
CREATE TABLE ANALYSIS_CRIME_STATISTICS (
    ID                       NUMBER        NOT NULL,
    DISTRICT_NAME            VARCHAR2(50),
    YEAR                     NUMBER,
    TOTAL_OCCURRENCE         NUMBER,
    TOTAL_ARREST             NUMBER,
    MURDER_OCCURRENCE        NUMBER,
    MURDER_ARREST            NUMBER,
    ROBBERY_OCCURRENCE       NUMBER,
    ROBBERY_ARREST           NUMBER,
    SEXUAL_CRIME_OCCURRENCE  NUMBER,
    SEXUAL_CRIME_ARREST      NUMBER,
    THEFT_OCCURRENCE         NUMBER,
    THEFT_ARREST             NUMBER,
    VIOLENCE_OCCURRENCE      NUMBER,
    VIOLENCE_ARREST          NUMBER,
    CONSTRAINT PK_ANALYSIS_CRIME_STATISTICS PRIMARY KEY (ID)
);
CREATE SEQUENCE SEQ_ANALYSIS_CRIME_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ============================================================
-- B-2. ANALYSIS_POPULATION_DENSITY (인구밀도)
-- ============================================================
CREATE TABLE ANALYSIS_POPULATION_DENSITY (
    ID                  NUMBER        NOT NULL,
    DISTRICT_NAME       VARCHAR2(100),
    YEAR                NUMBER,
    POPULATION_COUNT    NUMBER,
    AREA_SIZE           NUMBER(15,5),
    POPULATION_DENSITY  NUMBER(15,5),
    CONSTRAINT PK_ANALYSIS_POPULATION_DENSITY PRIMARY KEY (ID)
);
CREATE SEQUENCE SEQ_ANALYSIS_POPULATION_DENSITY START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

-- ============================================================
-- B-3. ANALYSIS_ENTERTAINMENT_STATISTICS (유흥업소 = 야간 상권)
-- ============================================================
CREATE TABLE ANALYSIS_ENTERTAINMENT_STATISTICS (
    ID                     NUMBER        NOT NULL,
    BUSINESS_STATUS_NAME   VARCHAR2(4000),
    PHONE_NUMBER           VARCHAR2(4000),
    JIBUN_ADDRESS          VARCHAR2(4000),
    ROAD_ADDRESS           VARCHAR2(4000),
    BUSINESS_NAME          VARCHAR2(4000),
    BUSINESS_CATEGORY      VARCHAR2(4000),
    HYGIENE_BUSINESS_TYPE  VARCHAR2(4000),
    LATITUDE               NUMBER(10,7),
    LONGITUDE              NUMBER(10,7),
    CONSTRAINT PK_ANALYSIS_ENTERTAINMENT_STATISTICS PRIMARY KEY (ID)
);
CREATE SEQUENCE SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
```

---

## C. VisitReservation (방문 예약)

> 이 5개 테이블은 프로젝트에 **완전한 권위 DDL 4종**(`docs/13. .../기타/SQL/01_tables.sql`~`04_indexes.sql`)이 있어 그대로 옮겼다. FK·CHECK·부분 유니크 인덱스(Function-Based)가 핵심. **실행 순서: 테이블 → 시퀀스 → 유니크제약 → 인덱스.**

```sql
-- ===== C-1. 테이블 (01_tables.sql) =====================================
CREATE TABLE VISIT_WINDOW (
    WINDOW_ID               NUMBER(19)      NOT NULL,
    PROPERTY_ID             CHAR(32)        NOT NULL,
    LEASE_TYPE              VARCHAR2(10)    NOT NULL,
    START_TIME              TIMESTAMP       NOT NULL,
    END_TIME                TIMESTAMP       NOT NULL,
    SLOT_DURATION_MINUTES   NUMBER(3)       DEFAULT 30       NOT NULL,
    STATUS                  VARCHAR2(10)    DEFAULT 'ACTIVE' NOT NULL,
    CREATED_AT              TIMESTAMP       NOT NULL,
    WITHDRAWN_AT            TIMESTAMP,
    CONSTRAINT PK_VISIT_WINDOW PRIMARY KEY (WINDOW_ID),
    CONSTRAINT CK_VISIT_WINDOW_LEASE_TYPE CHECK (LEASE_TYPE IN ('CHARTER','MONTHLY')),
    CONSTRAINT CK_VISIT_WINDOW_TIME       CHECK (END_TIME > START_TIME),
    CONSTRAINT CK_VISIT_WINDOW_STATUS     CHECK (STATUS IN ('ACTIVE','WITHDRAWN'))
);

CREATE TABLE VISIT_SLOT (
    SLOT_ID         NUMBER(19)      NOT NULL,
    WINDOW_ID       NUMBER(19)      NOT NULL,
    START_TIME      TIMESTAMP       NOT NULL,
    END_TIME        TIMESTAMP       NOT NULL,
    STATUS          VARCHAR2(15)    DEFAULT 'AVAILABLE' NOT NULL,
    CREATED_AT      TIMESTAMP       NOT NULL,
    CONSTRAINT PK_VISIT_SLOT PRIMARY KEY (SLOT_ID),
    CONSTRAINT FK_VISIT_SLOT_WINDOW FOREIGN KEY (WINDOW_ID) REFERENCES VISIT_WINDOW (WINDOW_ID),
    CONSTRAINT CK_VISIT_SLOT_TIME   CHECK (END_TIME > START_TIME),
    CONSTRAINT CK_VISIT_SLOT_STATUS CHECK (STATUS IN ('AVAILABLE','RESERVED','CLOSED','WITHDRAWN'))
);

CREATE TABLE VISIT_RESERVATION (
    RESERVATION_ID          NUMBER(19)      NOT NULL,
    SLOT_ID                 NUMBER(19)      NOT NULL,
    SEARCHER_USER_ID        VARCHAR2(100)   NOT NULL,
    STATUS                  VARCHAR2(15)    DEFAULT 'CONFIRMED' NOT NULL,
    CONFIRMED_AT            TIMESTAMP       NOT NULL,
    CANCELLED_AT            TIMESTAMP,
    INVALIDATED_AT          TIMESTAMP,
    VISIT_RESULT            VARCHAR2(10),
    RESULT_CLASSIFIED_AT    TIMESTAMP,
    CONSTRAINT PK_VISIT_RESERVATION PRIMARY KEY (RESERVATION_ID),
    CONSTRAINT FK_VISIT_RESERVATION_SLOT FOREIGN KEY (SLOT_ID) REFERENCES VISIT_SLOT (SLOT_ID),
    CONSTRAINT CK_VISIT_RESERVATION_STATUS CHECK (STATUS IN ('CONFIRMED','CANCELLED','INVALIDATED','COMPLETED')),
    CONSTRAINT CK_VISIT_RESERVATION_RESULT CHECK (VISIT_RESULT IS NULL OR VISIT_RESULT IN ('VISITED','NO_SHOW'))
);

CREATE TABLE REOPEN_SUBSCRIPTION (
    SUBSCRIPTION_ID         NUMBER(19)      NOT NULL,
    SLOT_ID                 NUMBER(19)      NOT NULL,
    SEARCHER_USER_ID        VARCHAR2(100)   NOT NULL,
    STATUS                  VARCHAR2(15)    DEFAULT 'ACTIVE' NOT NULL,
    SUBSCRIBED_AT           TIMESTAMP       NOT NULL,
    TERMINATED_AT           TIMESTAMP,
    TERMINATION_REASON      VARCHAR2(20),
    CONSTRAINT PK_REOPEN_SUBSCRIPTION PRIMARY KEY (SUBSCRIPTION_ID),
    CONSTRAINT FK_REOPEN_SUBSCRIPTION_SLOT FOREIGN KEY (SLOT_ID) REFERENCES VISIT_SLOT (SLOT_ID),
    CONSTRAINT CK_REOPEN_SUBSCRIPTION_STATUS CHECK (STATUS IN ('ACTIVE','FULFILLED','CANCELLED','EXPIRED'))
);

CREATE TABLE VISIT_NOTIFICATION (
    NOTIFICATION_ID         NUMBER(19)      NOT NULL,
    USER_ID                 VARCHAR2(100)   NOT NULL,
    NOTIFICATION_TYPE       VARCHAR2(30)    NOT NULL,
    RELATED_SLOT_ID         NUMBER(19),
    RELATED_RESERVATION_ID  NUMBER(19),
    RELATED_PROPERTY_ID     CHAR(32),
    MESSAGE                 VARCHAR2(500)   NOT NULL,
    IS_READ                 CHAR(1)         DEFAULT 'N' NOT NULL,
    CREATED_AT              TIMESTAMP       NOT NULL,
    CONSTRAINT PK_VISIT_NOTIFICATION PRIMARY KEY (NOTIFICATION_ID),
    CONSTRAINT CK_VISIT_NOTIFICATION_IS_READ CHECK (IS_READ IN ('Y','N'))
);

-- ===== C-2. 시퀀스 (02_sequences.sql) =================================
CREATE SEQUENCE SEQ_VISIT_WINDOW        START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_VISIT_SLOT          START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_VISIT_RESERVATION   START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_REOPEN_SUBSCRIPTION START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_VISIT_NOTIFICATION  START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;

-- ===== C-3. 추가 유니크 제약 / 부분 유니크 인덱스 (03_constraints.sql) ====
-- 슬롯: 같은 윈도우에 같은 시작시각 중복 INSERT 차단
ALTER TABLE VISIT_SLOT
    ADD CONSTRAINT UQ_VISIT_SLOT_WINDOW_START UNIQUE (WINDOW_ID, START_TIME);

-- 예약: 슬롯당 CONFIRMED 1건만 (Function-Based 부분 유니크 인덱스)
CREATE UNIQUE INDEX UQ_VISIT_RESERVATION_CONFIRMED_SLOT
    ON VISIT_RESERVATION ( CASE WHEN STATUS = 'CONFIRMED' THEN SLOT_ID END );

-- 구독: (슬롯, 탐색자)당 ACTIVE 1건만 (Function-Based 부분 유니크 인덱스)
CREATE UNIQUE INDEX UQ_REOPEN_SUBSCRIPTION_ACTIVE
    ON REOPEN_SUBSCRIPTION (
        CASE WHEN STATUS = 'ACTIVE' THEN SLOT_ID END,
        CASE WHEN STATUS = 'ACTIVE' THEN SEARCHER_USER_ID END
    );

-- ===== C-4. 성능 인덱스 (04_indexes.sql) ==============================
CREATE INDEX IX_VISIT_WINDOW_PROPERTY        ON VISIT_WINDOW (PROPERTY_ID, LEASE_TYPE, STATUS);
CREATE INDEX IX_VISIT_SLOT_STATUS_END        ON VISIT_SLOT (STATUS, END_TIME);
CREATE INDEX IX_VISIT_RESERVATION_SEARCHER   ON VISIT_RESERVATION (SEARCHER_USER_ID, STATUS);
CREATE INDEX IX_VISIT_RESERVATION_SLOT       ON VISIT_RESERVATION (SLOT_ID, STATUS);
CREATE INDEX IX_REOPEN_SUBSCRIPTION_SLOT     ON REOPEN_SUBSCRIPTION (SLOT_ID, STATUS);
CREATE INDEX IX_REOPEN_SUBSCRIPTION_SEARCHER ON REOPEN_SUBSCRIPTION (SEARCHER_USER_ID, STATUS);
CREATE INDEX IX_VISIT_NOTIFICATION_USER      ON VISIT_NOTIFICATION (USER_ID, IS_READ, CREATED_AT DESC);
```

> `VISIT_WINDOW.PROPERTY_ID`는 `PROPERTIES_*`를 논리적으로 가리키지만 **DB FK는 없다**(전세/월세 두 테이블 중 하나를 가리켜 단일 FK 불가). 코드도 조인으로만 접근.

---

## D. review (전세/월세 분리 — 현재 운영 테이블)

> 컬럼은 `ReviewCharter`/`ReviewMonthly`/`ReviewKeyword*`/`ReviewStatistics*` 엔티티 기준. `CONTENT`는 `@Lob` → **CLOB**. 통계 숫자 컬럼의 `DEFAULT 0`은 Java 필드 초기값과 맞춘 추가분(원본 단일테이블엔 없었음).

```sql
-- ===== D-1. 리뷰 원본 ==================================================
CREATE TABLE REVIEWS_CHARTER (
    REVIEW_ID    NUMBER(19)    NOT NULL,
    PROPERTY_ID  VARCHAR2(32)  NOT NULL,
    USER_ID      VARCHAR2(50)  NOT NULL,
    RATING       NUMBER        NOT NULL,
    CONTENT      CLOB          NOT NULL,
    CREATED_AT   TIMESTAMP     NOT NULL,
    UPDATED_AT   TIMESTAMP,
    CONSTRAINT PK_REVIEWS_CHARTER PRIMARY KEY (REVIEW_ID),
    CONSTRAINT UK_REVIEWS_CHARTER_PROP_USER UNIQUE (PROPERTY_ID, USER_ID)
);
CREATE TABLE REVIEWS_MONTHLY (
    REVIEW_ID    NUMBER(19)    NOT NULL,
    PROPERTY_ID  VARCHAR2(32)  NOT NULL,
    USER_ID      VARCHAR2(50)  NOT NULL,
    RATING       NUMBER        NOT NULL,
    CONTENT      CLOB          NOT NULL,
    CREATED_AT   TIMESTAMP     NOT NULL,
    UPDATED_AT   TIMESTAMP,
    CONSTRAINT PK_REVIEWS_MONTHLY PRIMARY KEY (REVIEW_ID),
    CONSTRAINT UK_REVIEWS_MONTHLY_PROP_USER UNIQUE (PROPERTY_ID, USER_ID)
);

-- ===== D-2. 리뷰 키워드 ===============================================
CREATE TABLE REVIEW_KEYWORDS_CHARTER (
    KEYWORD_ID  NUMBER(19)    NOT NULL,
    REVIEW_ID   NUMBER(19)    NOT NULL,
    KEYWORD     VARCHAR2(50)  NOT NULL,
    SCORE       NUMBER,
    CONSTRAINT PK_REVIEW_KEYWORDS_CHARTER PRIMARY KEY (KEYWORD_ID)
);
CREATE TABLE REVIEW_KEYWORDS_MONTHLY (
    KEYWORD_ID  NUMBER(19)    NOT NULL,
    REVIEW_ID   NUMBER(19)    NOT NULL,
    KEYWORD     VARCHAR2(50)  NOT NULL,
    SCORE       NUMBER,
    CONSTRAINT PK_REVIEW_KEYWORDS_MONTHLY PRIMARY KEY (KEYWORD_ID)
);
-- 키워드 집계/삭제(deleteByReviewId, aggregateKeywordStats)용 인덱스
CREATE INDEX IX_REVIEW_KEYWORDS_CHARTER_REVIEW ON REVIEW_KEYWORDS_CHARTER (REVIEW_ID);
CREATE INDEX IX_REVIEW_KEYWORDS_MONTHLY_REVIEW ON REVIEW_KEYWORDS_MONTHLY (REVIEW_ID);

-- ===== D-3. 리뷰 통계 (PK = PROPERTY_ID) =============================
CREATE TABLE REVIEW_STATISTICS_CHARTER (
    PROPERTY_ID             VARCHAR2(32)  NOT NULL,
    REVIEW_COUNT            NUMBER        DEFAULT 0 NOT NULL,
    AVG_RATING              NUMBER(3,2)   DEFAULT 0 NOT NULL,
    POSITIVE_KEYWORD_COUNT  NUMBER        DEFAULT 0 NOT NULL,
    NEGATIVE_KEYWORD_COUNT  NUMBER        DEFAULT 0 NOT NULL,
    LAST_CALCED             TIMESTAMP,
    CONSTRAINT PK_REVIEW_STATISTICS_CHARTER PRIMARY KEY (PROPERTY_ID)
);
CREATE TABLE REVIEW_STATISTICS_MONTHLY (
    PROPERTY_ID             VARCHAR2(32)  NOT NULL,
    REVIEW_COUNT            NUMBER        DEFAULT 0 NOT NULL,
    AVG_RATING              NUMBER(3,2)   DEFAULT 0 NOT NULL,
    POSITIVE_KEYWORD_COUNT  NUMBER        DEFAULT 0 NOT NULL,
    NEGATIVE_KEYWORD_COUNT  NUMBER        DEFAULT 0 NOT NULL,
    LAST_CALCED             TIMESTAMP,
    CONSTRAINT PK_REVIEW_STATISTICS_MONTHLY PRIMARY KEY (PROPERTY_ID)
);

-- ===== D-4. 시퀀스 (allocationSize=1) ================================
CREATE SEQUENCE SEQ_REVIEW_CHARTER_ID  START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_REVIEW_MONTHLY_ID  START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_KEYWORD_CHARTER_ID START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
CREATE SEQUENCE SEQ_KEYWORD_MONTHLY_ID START WITH 1 INCREMENT BY 1 CACHE 20 NOCYCLE;
```

> `REVIEW_KEYWORDS_*.REVIEW_ID → REVIEWS_*.REVIEW_ID` FK는 선택(서비스가 리뷰 삭제 전 키워드를 먼저 `deleteByReviewId`로 제거하므로 필수 아님). 원하면 `ON DELETE CASCADE` FK 추가 가능(§E).
> review의 `findByPropertyName`/`searchPropertiesByName` 네이티브 쿼리는 `REVIEWS_CHARTER ⨝ PROPERTIES_CHARTER ON r.PROPERTY_ID = p.PROPERTY_ID`로 조인 → review가 `PROPERTIES_*`(§A)에도 의존. `VARCHAR2(32)` vs `CHAR(32)` 비교지만 MD5 32자 고정이라 정상 매칭.

---

## E. 결정해야 할 지점 (설계 판단 영역)

위 DDL은 "현재 엔티티 기준"으로 채웠고, 아래는 확정이 필요한 대안.

1. **PROPERTIES 문자열 컬럼 크기 (⚠ 표시)** — 현재 엔티티는 `APT_NM 100·JIBUN 50·SGG_CD 10·ADDRESS 200`인데 2026-02-09 배포본 DDL은 전부 `255`였다. `ddl-auto=none`이라 엔티티 길이는 **강제되지 않으므로**, 국토부 배치 원천 데이터를 다시 적재한다면 **넉넉한 255 유지가 안전**(특히 `APT_NM`, `ADDRESS`). "코드와 일치"가 우선이면 위 값 그대로.
2. **숫자 정밀도** — `FLOOR NUMBER(3,0)`, `BUILD_YEAR NUMBER(4,0)`, `DEPOSIT NUMBER(15,0)` 등은 엔티티 Javadoc의 설계 의도. 구 배포본은 전부 정밀도 없는 `NUMBER`였다 → 느슨하게 두려면 `NUMBER`로.
3. **`PROPERTY_ID` 타입** — `PROPERTIES_*`는 엔티티가 `CHAR(32)` 명시, `PROPERTY_SYNC_FAILURES`·`REVIEWS_*`는 `VARCHAR2(32)`(엔티티대로 반영). 통일하려면 한쪽으로 맞추되 조인 대상(`CHAR(32)`)과 비교 시 MD5 32자 고정 전제 유지.
4. **enum CHECK 제약** — VisitReservation은 DB CHECK까지 거는 신규 컨벤션, PROPERTIES/review는 앱 레벨(`@Enumerated`)로만 강제. 일관성을 원하면 `PROPERTIES_*.DATA_SOURCE/STATUS` 등에 CHECK 추가 가능.
5. **키워드 FK(ON DELETE CASCADE)** 추가 여부 (§D-2 주석).

---

## F. 제외 / 선택 대상

- **구버전 死테이블 (선택)**: `REVIEWS`, `REVIEW_KEYWORDS`, `REVIEW_STATISTICS` ＋ 시퀀스 `SEQ_REVIEW_ID`, `SEQ_KEYWORD_ID` — 엔티티/리포는 남아있으나 **어떤 서비스도 사용 안 함**. 운영엔 불필요. 과거 데이터 보존·롤백 목적이 아니면 생성하지 않아도 됨.
- **이번 범위 제외**: 채팅(요청대로), 그리고 4개 패키지가 안 쓰는 타 패키지 테이블 — `MEMBERTBL`, `USERENTITY(+_ROLES)`, `WHEREBOARD`, `COMMENTTBL`, `MAPDATA`, `ARRESTRATE`, `CCTV(_GEO)`, `POLICEOFFICE(_GEO)`, recommand가 안 쓰는 **나머지 15개 `ANALYSIS_*`**. 단, `SEARCHER_USER_ID`/`REGISTERED_USER_ID`/`USER_ID` 등은 회원 테이블을 **FK 없이 문자열로만** 참조하므로, 회원/인증 테이블(계정)이 그대로 있으면 동작 무관.

---

## G. 권장 실행 순서

1. **(§A)** `PROPERTIES_CHARTER` → `PROPERTIES_MONTHLY` → 인덱스 → `PROPERTY_SYNC_FAILURES`
2. **(§B)** `ANALYSIS` 3종 + 시퀀스 3종
3. **(§C)** VISIT 테이블 5종 → 시퀀스 5종 → 유니크제약/부분인덱스 3종 → 성능인덱스 7종 *(반드시 이 순서; FK 때문에 WINDOW→SLOT→RESERVATION/SUBSCRIPTION)*
4. **(§D)** `REVIEWS_*` → `REVIEW_KEYWORDS_*` → `REVIEW_STATISTICS_*` → 시퀀스 4종

> 기존 테이블을 진짜로 "처음부터" 갈아엎을 거면, 초기화용 `DROP TABLE ... CASCADE CONSTRAINTS PURGE` / `DROP SEQUENCE ...` 블록을 위 **역순(자식→부모)** 으로 별도 작성 필요(데이터 삭제 작업이라 본 문서엔 미포함).
