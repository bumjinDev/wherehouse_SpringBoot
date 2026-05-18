-- ============================================================
-- 매물 대량 데이터 삽입 스크립트
-- PROPERTIES_MONTHLY : 150만건 (월세)
-- PROPERTIES_CHARTER : 150만건 (전세)
-- 대상 DB : Oracle XE (SCOTT 스키마)
-- ============================================================
-- 실행 전 주의사항:
--   1) SET SERVEROUTPUT ON 으로 진행 상황 확인
--   2) Oracle 12c 이상 필요 (STANDARD_HASH 함수)
--   3) UNDO tablespace 여유 확인 (배치 COMMIT 으로 최소화)
--   4) 전체 소요시간 약 10~30분 (디스크 성능에 따라 상이)
-- ============================================================

SET SERVEROUTPUT ON SIZE UNLIMITED;
ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF3';

-- ============================================================
-- 0. 사전 준비: 인덱스 UNUSABLE + NOLOGGING (선택 사항)
--    * 삽입 속도 2~5배 향상
--    * 삽입 후 반드시 Section 3 에서 REBUILD 해야 함
-- ============================================================

-- 인덱스 비활성화 (존재하지 않는 인덱스는 무시)
BEGIN
    FOR idx IN (
        SELECT INDEX_NAME, TABLE_NAME
          FROM USER_INDEXES
         WHERE TABLE_NAME IN ('PROPERTIES_MONTHLY', 'PROPERTIES_CHARTER')
           AND INDEX_TYPE = 'NORMAL'
           AND INDEX_NAME NOT LIKE 'PK_%'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER INDEX ' || idx.INDEX_NAME || ' UNUSABLE';
        DBMS_OUTPUT.PUT_LINE('[INDEX OFF] ' || idx.TABLE_NAME || '.' || idx.INDEX_NAME);
    END LOOP;
END;
/

ALTER TABLE PROPERTIES_MONTHLY NOLOGGING;
ALTER TABLE PROPERTIES_CHARTER NOLOGGING;


-- ============================================================
-- 1. PROPERTIES_MONTHLY 150만건 삽입 (월세)
-- ============================================================
DECLARE
    c_total    CONSTANT PLS_INTEGER := 1500000;
    c_batch    CONSTANT PLS_INTEGER := 50000;   -- 배치당 5만건
    c_batches  CONSTANT PLS_INTEGER := c_total / c_batch;

    v_start    PLS_INTEGER;
    v_cnt      PLS_INTEGER;
    v_elapsed  NUMBER;
    v_t0       TIMESTAMP := SYSTIMESTAMP;
BEGIN
    DBMS_OUTPUT.PUT_LINE('=== PROPERTIES_MONTHLY 삽입 시작 (' || c_total || '건) ===');
    DBMS_OUTPUT.PUT_LINE('배치 크기: ' || c_batch || '건 x ' || c_batches || '회');

    FOR b IN 0 .. c_batches - 1 LOOP
        v_start := b * c_batch + 1;

        INSERT /*+ APPEND */ INTO PROPERTIES_MONTHLY (
            PROPERTY_ID, APT_NM, EXCLU_USE_AR, FLOOR, BUILD_YEAR,
            DEAL_DATE, DEPOSIT, MONTHLY_RENT, LEASE_TYPE,
            UMD_NM, JIBUN, SGG_CD, ADDRESS, AREA_IN_PYEONG,
            RGST_DATE, DISTRICT_NAME, LAST_UPDATED,
            DATA_SOURCE, STATUS
        )
        WITH apt AS (
            SELECT 0 id, '래미안' nm FROM DUAL UNION ALL
            SELECT 1, '자이' FROM DUAL UNION ALL
            SELECT 2, '힐스테이트' FROM DUAL UNION ALL
            SELECT 3, '푸르지오' FROM DUAL UNION ALL
            SELECT 4, '아이파크' FROM DUAL UNION ALL
            SELECT 5, '리슈빌' FROM DUAL UNION ALL
            SELECT 6, '더샵' FROM DUAL UNION ALL
            SELECT 7, 'e편한세상' FROM DUAL UNION ALL
            SELECT 8, '롯데캐슬' FROM DUAL UNION ALL
            SELECT 9, '현대아파트' FROM DUAL UNION ALL
            SELECT 10, '삼성아파트' FROM DUAL UNION ALL
            SELECT 11, '두산위브' FROM DUAL UNION ALL
            SELECT 12, '한신아파트' FROM DUAL UNION ALL
            SELECT 13, '대우아파트' FROM DUAL UNION ALL
            SELECT 14, '코오롱하늘채' FROM DUAL UNION ALL
            SELECT 15, '포스코더샵' FROM DUAL UNION ALL
            SELECT 16, '벽산블루밍' FROM DUAL UNION ALL
            SELECT 17, '한화꿈에그린' FROM DUAL UNION ALL
            SELECT 18, '대림아크로리버' FROM DUAL UNION ALL
            SELECT 19, 'SK뷰' FROM DUAL UNION ALL
            SELECT 20, '금호어울림' FROM DUAL UNION ALL
            SELECT 21, '동아아파트' FROM DUAL UNION ALL
            SELECT 22, '우성아파트' FROM DUAL UNION ALL
            SELECT 23, '쌍용예가' FROM DUAL UNION ALL
            SELECT 24, '한라비발디' FROM DUAL UNION ALL
            SELECT 25, '중흥S클래스' FROM DUAL UNION ALL
            SELECT 26, '호반써밋' FROM DUAL UNION ALL
            SELECT 27, '동원로얄듀크' FROM DUAL UNION ALL
            SELECT 28, '우미린' FROM DUAL UNION ALL
            SELECT 29, '태영데시앙' FROM DUAL
        ),
        dist AS (
            SELECT 0 id, '11110' cd, '종로구' nm FROM DUAL UNION ALL
            SELECT 1, '11140', '중구' FROM DUAL UNION ALL
            SELECT 2, '11170', '용산구' FROM DUAL UNION ALL
            SELECT 3, '11200', '성동구' FROM DUAL UNION ALL
            SELECT 4, '11215', '광진구' FROM DUAL UNION ALL
            SELECT 5, '11230', '동대문구' FROM DUAL UNION ALL
            SELECT 6, '11260', '중랑구' FROM DUAL UNION ALL
            SELECT 7, '11290', '성북구' FROM DUAL UNION ALL
            SELECT 8, '11305', '강북구' FROM DUAL UNION ALL
            SELECT 9, '11320', '도봉구' FROM DUAL UNION ALL
            SELECT 10, '11350', '노원구' FROM DUAL UNION ALL
            SELECT 11, '11380', '은평구' FROM DUAL UNION ALL
            SELECT 12, '11410', '서대문구' FROM DUAL UNION ALL
            SELECT 13, '11440', '마포구' FROM DUAL UNION ALL
            SELECT 14, '11470', '양천구' FROM DUAL UNION ALL
            SELECT 15, '11500', '강서구' FROM DUAL UNION ALL
            SELECT 16, '11530', '구로구' FROM DUAL UNION ALL
            SELECT 17, '11545', '금천구' FROM DUAL UNION ALL
            SELECT 18, '11560', '영등포구' FROM DUAL UNION ALL
            SELECT 19, '11590', '동작구' FROM DUAL UNION ALL
            SELECT 20, '11620', '관악구' FROM DUAL UNION ALL
            SELECT 21, '11650', '서초구' FROM DUAL UNION ALL
            SELECT 22, '11680', '강남구' FROM DUAL UNION ALL
            SELECT 23, '11710', '송파구' FROM DUAL UNION ALL
            SELECT 24, '11740', '강동구' FROM DUAL
        ),
        dong AS (
            SELECT 0 id, '역삼동' nm FROM DUAL UNION ALL
            SELECT 1, '삼성동' FROM DUAL UNION ALL
            SELECT 2, '논현동' FROM DUAL UNION ALL
            SELECT 3, '청담동' FROM DUAL UNION ALL
            SELECT 4, '대치동' FROM DUAL UNION ALL
            SELECT 5, '잠실동' FROM DUAL UNION ALL
            SELECT 6, '서초동' FROM DUAL UNION ALL
            SELECT 7, '방배동' FROM DUAL UNION ALL
            SELECT 8, '반포동' FROM DUAL UNION ALL
            SELECT 9, '양재동' FROM DUAL UNION ALL
            SELECT 10, '이태원동' FROM DUAL UNION ALL
            SELECT 11, '한남동' FROM DUAL UNION ALL
            SELECT 12, '회기동' FROM DUAL UNION ALL
            SELECT 13, '장안동' FROM DUAL UNION ALL
            SELECT 14, '천호동' FROM DUAL UNION ALL
            SELECT 15, '상암동' FROM DUAL UNION ALL
            SELECT 16, '신도림동' FROM DUAL UNION ALL
            SELECT 17, '구로동' FROM DUAL UNION ALL
            SELECT 18, '목동' FROM DUAL UNION ALL
            SELECT 19, '신정동' FROM DUAL UNION ALL
            SELECT 20, '화곡동' FROM DUAL UNION ALL
            SELECT 21, '등촌동' FROM DUAL UNION ALL
            SELECT 22, '신림동' FROM DUAL UNION ALL
            SELECT 23, '봉천동' FROM DUAL UNION ALL
            SELECT 24, '미아동' FROM DUAL UNION ALL
            SELECT 25, '방학동' FROM DUAL UNION ALL
            SELECT 26, '상계동' FROM DUAL UNION ALL
            SELECT 27, '중계동' FROM DUAL UNION ALL
            SELECT 28, '수유동' FROM DUAL UNION ALL
            SELECT 29, '번동' FROM DUAL
        ),
        nums AS (
            SELECT LEVEL + v_start - 1 AS rn
              FROM DUAL
           CONNECT BY LEVEL <= c_batch
        )
        SELECT
            RAWTOHEX(STANDARD_HASH(TO_CHAR(n.rn) || '_M', 'MD5'))          AS property_id,
            a.nm                                                            AS apt_nm,
            ROUND(33 + MOD(n.rn * 29, 13200) / 100, 2)                     AS exclu_use_ar,
            MOD(n.rn * 11, 30) + 1                                         AS floor,
            1985 + MOD(n.rn * 7, 41)                                       AS build_year,
            TO_CHAR(DATE '2023-01-01' + MOD(n.rn * 3, 1095), 'YYYY-MM-DD') AS deal_date,
            1000 + MOD(n.rn * 23, 19001)                                   AS deposit,
            30 + MOD(n.rn * 19, 271)                                       AS monthly_rent,
            '월세'                                                          AS lease_type,
            d2.nm                                                           AS umd_nm,
            TO_CHAR(MOD(n.rn * 17, 999) + 1)
                || '-' || TO_CHAR(MOD(n.rn * 13, 99) + 1)                  AS jibun,
            d1.cd                                                           AS sgg_cd,
            d2.nm || ' ' || TO_CHAR(MOD(n.rn * 17, 999) + 1)
                || '-' || TO_CHAR(MOD(n.rn * 13, 99) + 1)                  AS address,
            ROUND((33 + MOD(n.rn * 29, 13200) / 100) / 3.3058, 2)          AS area_in_pyeong,
            TO_CHAR(SYSDATE - MOD(n.rn, 365), 'YYYYMMDDHH24MI')            AS rgst_date,
            d1.nm                                                           AS district_name,
            SYSTIMESTAMP                                                     AS last_updated,
            'BATCH'                                                         AS data_source,
            'ACTIVE'                                                        AS status
        FROM nums n
        JOIN apt  a  ON a.id  = MOD(n.rn, 30)
        JOIN dist d1 ON d1.id = MOD(n.rn, 25)
        JOIN dong d2 ON d2.id = MOD(TRUNC(n.rn / 25), 30);

        COMMIT;

        v_cnt := (b + 1) * c_batch;
        v_elapsed := EXTRACT(SECOND FROM (SYSTIMESTAMP - v_t0))
                   + EXTRACT(MINUTE FROM (SYSTIMESTAMP - v_t0)) * 60;
        DBMS_OUTPUT.PUT_LINE(
            '[MONTHLY] ' || LPAD(b + 1, 2) || '/' || c_batches
            || '  누적 ' || TO_CHAR(v_cnt, '9,999,999') || '건'
            || '  경과 ' || ROUND(v_elapsed, 1) || '초'
        );
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('=== PROPERTIES_MONTHLY 삽입 완료 ===');
END;
/


-- ============================================================
-- 2. PROPERTIES_CHARTER 150만건 삽입 (전세)
-- ============================================================
DECLARE
    c_total    CONSTANT PLS_INTEGER := 1500000;
    c_batch    CONSTANT PLS_INTEGER := 50000;
    c_batches  CONSTANT PLS_INTEGER := c_total / c_batch;

    v_start    PLS_INTEGER;
    v_cnt      PLS_INTEGER;
    v_elapsed  NUMBER;
    v_t0       TIMESTAMP := SYSTIMESTAMP;
BEGIN
    DBMS_OUTPUT.PUT_LINE('=== PROPERTIES_CHARTER 삽입 시작 (' || c_total || '건) ===');
    DBMS_OUTPUT.PUT_LINE('배치 크기: ' || c_batch || '건 x ' || c_batches || '회');

    FOR b IN 0 .. c_batches - 1 LOOP
        v_start := b * c_batch + 1;

        INSERT /*+ APPEND */ INTO PROPERTIES_CHARTER (
            PROPERTY_ID, APT_NM, EXCLU_USE_AR, FLOOR, BUILD_YEAR,
            DEAL_DATE, DEPOSIT, LEASE_TYPE,
            UMD_NM, JIBUN, SGG_CD, ADDRESS, AREA_IN_PYEONG,
            RGST_DATE, DISTRICT_NAME, LAST_UPDATED,
            DATA_SOURCE, STATUS
        )
        WITH apt AS (
            SELECT 0 id, '래미안' nm FROM DUAL UNION ALL
            SELECT 1, '자이' FROM DUAL UNION ALL
            SELECT 2, '힐스테이트' FROM DUAL UNION ALL
            SELECT 3, '푸르지오' FROM DUAL UNION ALL
            SELECT 4, '아이파크' FROM DUAL UNION ALL
            SELECT 5, '리슈빌' FROM DUAL UNION ALL
            SELECT 6, '더샵' FROM DUAL UNION ALL
            SELECT 7, 'e편한세상' FROM DUAL UNION ALL
            SELECT 8, '롯데캐슬' FROM DUAL UNION ALL
            SELECT 9, '현대아파트' FROM DUAL UNION ALL
            SELECT 10, '삼성아파트' FROM DUAL UNION ALL
            SELECT 11, '두산위브' FROM DUAL UNION ALL
            SELECT 12, '한신아파트' FROM DUAL UNION ALL
            SELECT 13, '대우아파트' FROM DUAL UNION ALL
            SELECT 14, '코오롱하늘채' FROM DUAL UNION ALL
            SELECT 15, '포스코더샵' FROM DUAL UNION ALL
            SELECT 16, '벽산블루밍' FROM DUAL UNION ALL
            SELECT 17, '한화꿈에그린' FROM DUAL UNION ALL
            SELECT 18, '대림아크로리버' FROM DUAL UNION ALL
            SELECT 19, 'SK뷰' FROM DUAL UNION ALL
            SELECT 20, '금호어울림' FROM DUAL UNION ALL
            SELECT 21, '동아아파트' FROM DUAL UNION ALL
            SELECT 22, '우성아파트' FROM DUAL UNION ALL
            SELECT 23, '쌍용예가' FROM DUAL UNION ALL
            SELECT 24, '한라비발디' FROM DUAL UNION ALL
            SELECT 25, '중흥S클래스' FROM DUAL UNION ALL
            SELECT 26, '호반써밋' FROM DUAL UNION ALL
            SELECT 27, '동원로얄듀크' FROM DUAL UNION ALL
            SELECT 28, '우미린' FROM DUAL UNION ALL
            SELECT 29, '태영데시앙' FROM DUAL
        ),
        dist AS (
            SELECT 0 id, '11110' cd, '종로구' nm FROM DUAL UNION ALL
            SELECT 1, '11140', '중구' FROM DUAL UNION ALL
            SELECT 2, '11170', '용산구' FROM DUAL UNION ALL
            SELECT 3, '11200', '성동구' FROM DUAL UNION ALL
            SELECT 4, '11215', '광진구' FROM DUAL UNION ALL
            SELECT 5, '11230', '동대문구' FROM DUAL UNION ALL
            SELECT 6, '11260', '중랑구' FROM DUAL UNION ALL
            SELECT 7, '11290', '성북구' FROM DUAL UNION ALL
            SELECT 8, '11305', '강북구' FROM DUAL UNION ALL
            SELECT 9, '11320', '도봉구' FROM DUAL UNION ALL
            SELECT 10, '11350', '노원구' FROM DUAL UNION ALL
            SELECT 11, '11380', '은평구' FROM DUAL UNION ALL
            SELECT 12, '11410', '서대문구' FROM DUAL UNION ALL
            SELECT 13, '11440', '마포구' FROM DUAL UNION ALL
            SELECT 14, '11470', '양천구' FROM DUAL UNION ALL
            SELECT 15, '11500', '강서구' FROM DUAL UNION ALL
            SELECT 16, '11530', '구로구' FROM DUAL UNION ALL
            SELECT 17, '11545', '금천구' FROM DUAL UNION ALL
            SELECT 18, '11560', '영등포구' FROM DUAL UNION ALL
            SELECT 19, '11590', '동작구' FROM DUAL UNION ALL
            SELECT 20, '11620', '관악구' FROM DUAL UNION ALL
            SELECT 21, '11650', '서초구' FROM DUAL UNION ALL
            SELECT 22, '11680', '강남구' FROM DUAL UNION ALL
            SELECT 23, '11710', '송파구' FROM DUAL UNION ALL
            SELECT 24, '11740', '강동구' FROM DUAL
        ),
        dong AS (
            SELECT 0 id, '역삼동' nm FROM DUAL UNION ALL
            SELECT 1, '삼성동' FROM DUAL UNION ALL
            SELECT 2, '논현동' FROM DUAL UNION ALL
            SELECT 3, '청담동' FROM DUAL UNION ALL
            SELECT 4, '대치동' FROM DUAL UNION ALL
            SELECT 5, '잠실동' FROM DUAL UNION ALL
            SELECT 6, '서초동' FROM DUAL UNION ALL
            SELECT 7, '방배동' FROM DUAL UNION ALL
            SELECT 8, '반포동' FROM DUAL UNION ALL
            SELECT 9, '양재동' FROM DUAL UNION ALL
            SELECT 10, '이태원동' FROM DUAL UNION ALL
            SELECT 11, '한남동' FROM DUAL UNION ALL
            SELECT 12, '회기동' FROM DUAL UNION ALL
            SELECT 13, '장안동' FROM DUAL UNION ALL
            SELECT 14, '천호동' FROM DUAL UNION ALL
            SELECT 15, '상암동' FROM DUAL UNION ALL
            SELECT 16, '신도림동' FROM DUAL UNION ALL
            SELECT 17, '구로동' FROM DUAL UNION ALL
            SELECT 18, '목동' FROM DUAL UNION ALL
            SELECT 19, '신정동' FROM DUAL UNION ALL
            SELECT 20, '화곡동' FROM DUAL UNION ALL
            SELECT 21, '등촌동' FROM DUAL UNION ALL
            SELECT 22, '신림동' FROM DUAL UNION ALL
            SELECT 23, '봉천동' FROM DUAL UNION ALL
            SELECT 24, '미아동' FROM DUAL UNION ALL
            SELECT 25, '방학동' FROM DUAL UNION ALL
            SELECT 26, '상계동' FROM DUAL UNION ALL
            SELECT 27, '중계동' FROM DUAL UNION ALL
            SELECT 28, '수유동' FROM DUAL UNION ALL
            SELECT 29, '번동' FROM DUAL
        ),
        nums AS (
            SELECT LEVEL + v_start - 1 AS rn
              FROM DUAL
           CONNECT BY LEVEL <= c_batch
        )
        SELECT
            RAWTOHEX(STANDARD_HASH(TO_CHAR(n.rn) || '_C', 'MD5'))          AS property_id,
            a.nm                                                            AS apt_nm,
            ROUND(33 + MOD(n.rn * 29, 13200) / 100, 2)                     AS exclu_use_ar,
            MOD(n.rn * 11, 30) + 1                                         AS floor,
            1985 + MOD(n.rn * 7, 41)                                       AS build_year,
            TO_CHAR(DATE '2023-01-01' + MOD(n.rn * 3, 1095), 'YYYY-MM-DD') AS deal_date,
            10000 + MOD(n.rn * 31, 70001)                                  AS deposit,
            '전세'                                                          AS lease_type,
            d2.nm                                                           AS umd_nm,
            TO_CHAR(MOD(n.rn * 17, 999) + 1)
                || '-' || TO_CHAR(MOD(n.rn * 13, 99) + 1)                  AS jibun,
            d1.cd                                                           AS sgg_cd,
            d2.nm || ' ' || TO_CHAR(MOD(n.rn * 17, 999) + 1)
                || '-' || TO_CHAR(MOD(n.rn * 13, 99) + 1)                  AS address,
            ROUND((33 + MOD(n.rn * 29, 13200) / 100) / 3.3058, 2)          AS area_in_pyeong,
            TO_CHAR(SYSDATE - MOD(n.rn, 365), 'YYYYMMDDHH24MI')            AS rgst_date,
            d1.nm                                                           AS district_name,
            SYSTIMESTAMP                                                     AS last_updated,
            'BATCH'                                                         AS data_source,
            'ACTIVE'                                                        AS status
        FROM nums n
        JOIN apt  a  ON a.id  = MOD(n.rn, 30)
        JOIN dist d1 ON d1.id = MOD(n.rn, 25)
        JOIN dong d2 ON d2.id = MOD(TRUNC(n.rn / 25), 30);

        COMMIT;

        v_cnt := (b + 1) * c_batch;
        v_elapsed := EXTRACT(SECOND FROM (SYSTIMESTAMP - v_t0))
                   + EXTRACT(MINUTE FROM (SYSTIMESTAMP - v_t0)) * 60;
        DBMS_OUTPUT.PUT_LINE(
            '[CHARTER] ' || LPAD(b + 1, 2) || '/' || c_batches
            || '  누적 ' || TO_CHAR(v_cnt, '9,999,999') || '건'
            || '  경과 ' || ROUND(v_elapsed, 1) || '초'
        );
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('=== PROPERTIES_CHARTER 삽입 완료 ===');
END;
/


-- ============================================================
-- 3. 사후 처리: 인덱스 REBUILD + LOGGING 복원
-- ============================================================
BEGIN
    FOR idx IN (
        SELECT INDEX_NAME, TABLE_NAME
          FROM USER_INDEXES
         WHERE TABLE_NAME IN ('PROPERTIES_MONTHLY', 'PROPERTIES_CHARTER')
           AND STATUS = 'UNUSABLE'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER INDEX ' || idx.INDEX_NAME || ' REBUILD';
        DBMS_OUTPUT.PUT_LINE('[INDEX REBUILD] ' || idx.TABLE_NAME || '.' || idx.INDEX_NAME);
    END LOOP;
END;
/

ALTER TABLE PROPERTIES_MONTHLY LOGGING;
ALTER TABLE PROPERTIES_CHARTER LOGGING;


-- ============================================================
-- 4. 통계 수집 (CBO 옵티마이저용)
-- ============================================================
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => 'SCOTT',
        tabname => 'PROPERTIES_MONTHLY',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE,
        cascade => TRUE
    );
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname => 'SCOTT',
        tabname => 'PROPERTIES_CHARTER',
        estimate_percent => DBMS_STATS.AUTO_SAMPLE_SIZE,
        cascade => TRUE
    );
    DBMS_OUTPUT.PUT_LINE('통계 수집 완료');
END;
/


-- ============================================================
-- 5. 검증 쿼리
-- ============================================================

-- 건수 확인
SELECT 'PROPERTIES_MONTHLY' AS TABLE_NAME, COUNT(*) AS ROW_COUNT FROM PROPERTIES_MONTHLY
UNION ALL
SELECT 'PROPERTIES_CHARTER', COUNT(*) FROM PROPERTIES_CHARTER;

-- 구별 분포 확인
SELECT DISTRICT_NAME, COUNT(*) AS CNT
  FROM PROPERTIES_MONTHLY
 GROUP BY DISTRICT_NAME
 ORDER BY CNT DESC;

-- 가격대 분포 (월세)
SELECT
    CASE
        WHEN MONTHLY_RENT <= 50  THEN '50만 이하'
        WHEN MONTHLY_RENT <= 100 THEN '51~100만'
        WHEN MONTHLY_RENT <= 200 THEN '101~200만'
        ELSE '200만 초과'
    END AS RENT_RANGE,
    COUNT(*) AS CNT
  FROM PROPERTIES_MONTHLY
 GROUP BY
    CASE
        WHEN MONTHLY_RENT <= 50  THEN '50만 이하'
        WHEN MONTHLY_RENT <= 100 THEN '51~100만'
        WHEN MONTHLY_RENT <= 200 THEN '101~200만'
        ELSE '200만 초과'
    END
 ORDER BY RENT_RANGE;

-- 보증금 분포 (전세)
SELECT
    CASE
        WHEN DEPOSIT <= 20000 THEN '2억 이하'
        WHEN DEPOSIT <= 40000 THEN '2~4억'
        WHEN DEPOSIT <= 60000 THEN '4~6억'
        ELSE '6억 초과'
    END AS DEPOSIT_RANGE,
    COUNT(*) AS CNT
  FROM PROPERTIES_CHARTER
 GROUP BY
    CASE
        WHEN DEPOSIT <= 20000 THEN '2억 이하'
        WHEN DEPOSIT <= 40000 THEN '2~4억'
        WHEN DEPOSIT <= 60000 THEN '4~6억'
        ELSE '6억 초과'
    END
 ORDER BY DEPOSIT_RANGE;

-- 샘플 데이터 확인
SELECT * FROM PROPERTIES_MONTHLY WHERE ROWNUM <= 5;
SELECT * FROM PROPERTIES_CHARTER WHERE ROWNUM <= 5;


-- ============================================================
-- [참고] 롤백이 필요한 경우
-- ============================================================
-- TRUNCATE TABLE PROPERTIES_MONTHLY;
-- TRUNCATE TABLE PROPERTIES_CHARTER;
