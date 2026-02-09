-- ============================================================
-- Wherehouse 프로젝트 - 안전성 분석 가공 데이터 테이블 통합 DDL
-- ============================================================
-- 생성일: 2026-02-09
-- 근거: AnalyzeSafetyScore 프로젝트 JPA Entity 소스코드 기반
-- 대상: 원격 Oracle 서버 (SCOTT 스키마)
-- 테이블 수: 18개 (ANALYSIS_* 가공 데이터 테이블)
-- ============================================================
-- 주의사항:
--   1. 기존 테이블이 있으면 DROP 후 재생성합니다.
--   2. 제약조건 없이 순수 분석 목적의 테이블 구조입니다.
--   3. 시퀀스는 START WITH 1로 생성합니다.
--      (데이터 마이그레이션 후 시퀀스 값 재조정 필요)
-- ============================================================


-- ============================================================
-- 1. ANALYSIS_CRIME_STATISTICS (범죄 통계)
--    용도: 종속변수 - 서울시 25개 자치구별 5대범죄 통계
--    ★ wherehouse 배치에서 안전성 점수 계산에 직접 사용
-- ============================================================
DROP TABLE ANALYSIS_CRIME_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_CRIME_STATISTICS;

CREATE TABLE ANALYSIS_CRIME_STATISTICS (
    ID                       NUMBER,
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
    VIOLENCE_ARREST          NUMBER
);

CREATE SEQUENCE SEQ_ANALYSIS_CRIME_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 2. ANALYSIS_CCTV_STATISTICS (CCTV 인프라)
--    용도: 독립변수 - CCTV 설치 밀도
-- ============================================================
DROP TABLE ANALYSIS_CCTV_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_CCTV_STATISTICS;

CREATE TABLE ANALYSIS_CCTV_STATISTICS (
    ID                  NUMBER,
    MANAGEMENT_AGENCY   VARCHAR2(100),
    ROAD_ADDRESS        VARCHAR2(200),
    JIBUN_ADDRESS       VARCHAR2(200),
    LATITUDE            NUMBER(10,8),
    LONGITUDE           NUMBER(10,8)
);

CREATE SEQUENCE SEQ_ANALYSIS_CCTV_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 3. ANALYSIS_PC_BANG_STATISTICS (PC방 시설)
--    용도: 독립변수 - PC방 밀도
-- ============================================================
DROP TABLE ANALYSIS_PC_BANG_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_PC_BANG_STATISTICS;

CREATE TABLE ANALYSIS_PC_BANG_STATISTICS (
    ID                    NUMBER,
    DISTRICT_CODE         VARCHAR2(20),
    MANAGEMENT_NUMBER     VARCHAR2(100),
    BUSINESS_STATUS_NAME  VARCHAR2(100),
    JIBUN_ADDRESS         VARCHAR2(1000),
    ROAD_ADDRESS          VARCHAR2(1000),
    BUSINESS_NAME         VARCHAR2(500),
    LATITUDE              NUMBER(10,8),
    LONGITUDE             NUMBER(10,8),
    GEOCODING_STATUS      VARCHAR2(4000)
);

CREATE SEQUENCE SEQ_ANALYSIS_PC_BANG_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 4. ANALYSIS_STREETLIGHT_STATISTICS (가로등 인프라)
--    용도: 독립변수 - 가로등 설치 밀도
-- ============================================================
DROP TABLE ANALYSIS_STREETLIGHT_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_STREETLIGHT_STATISTICS;

CREATE TABLE ANALYSIS_STREETLIGHT_STATISTICS (
    ID                  NUMBER,
    MANAGEMENT_NUMBER   VARCHAR2(4000),
    DISTRICT_NAME       VARCHAR2(4000),
    DONG_NAME           VARCHAR2(4000),
    ROAD_ADDRESS        VARCHAR2(4000),
    JIBUN_ADDRESS       VARCHAR2(4000),
    LATITUDE            NUMBER,
    LONGITUDE           NUMBER
);

CREATE SEQUENCE SEQ_ANALYSIS_STREETLIGHT_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 5. ANALYSIS_HOSPITAL_DATA (의료 시설)
--    용도: 독립변수 - 의료시설 밀도
-- ============================================================
DROP TABLE ANALYSIS_HOSPITAL_DATA CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_HOSPITAL_DATA;

CREATE TABLE ANALYSIS_HOSPITAL_DATA (
    ID                    NUMBER,
    BUSINESS_NAME         VARCHAR2(4000),
    BUSINESS_TYPE_NAME    VARCHAR2(4000),
    DETAILED_STATUS_NAME  VARCHAR2(4000),
    PHONE_NUMBER          VARCHAR2(4000),
    LOT_ADDRESS           VARCHAR2(4000),
    ROAD_ADDRESS          VARCHAR2(4000),
    LATITUDE              NUMBER(10,7),
    LONGITUDE             NUMBER(10,7),
    CREATED_AT            TIMESTAMP,
    UPDATED_AT            TIMESTAMP
);

CREATE SEQUENCE SEQ_ANALYSIS_HOSPITAL_DATA START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 6. ANALYSIS_POLICE_FACILITY (경찰시설)
--    용도: 독립변수 - 치안 인프라 (참고용, 회귀분석 제외)
-- ============================================================
DROP TABLE ANALYSIS_POLICE_FACILITY CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_POLICE_FACILITY;

CREATE TABLE ANALYSIS_POLICE_FACILITY (
    ID                    NUMBER,
    SERIAL_NO             NUMBER,
    CITY_PROVINCE         VARCHAR2(4000),
    POLICE_STATION        VARCHAR2(4000),
    FACILITY_NAME         VARCHAR2(4000),
    FACILITY_TYPE         VARCHAR2(4000),
    PHONE_NUMBER          VARCHAR2(4000),
    ADDRESS               VARCHAR2(4000),
    COORD_X               NUMBER,
    COORD_Y               NUMBER,
    DISTRICT_NAME         VARCHAR2(4000),
    GEOCODING_STATUS      VARCHAR2(4000),
    API_RESPONSE_ADDRESS  VARCHAR2(4000)
);

CREATE SEQUENCE SEQ_ANALYSIS_POLICE_FACILITY START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 7. ANALYSIS_KARAOKE_ROOMS (노래연습장)
--    용도: 독립변수 - 노래연습장 밀도
-- ============================================================
DROP TABLE ANALYSIS_KARAOKE_ROOMS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_KARAOKE_ROOMS;

CREATE TABLE ANALYSIS_KARAOKE_ROOMS (
    ID                         NUMBER,
    DISTRICT_CODE              VARCHAR2(4000),
    MANAGEMENT_NUMBER          VARCHAR2(4000),
    BUSINESS_STATUS_NAME       VARCHAR2(4000),
    PHONE_NUMBER               VARCHAR2(4000),
    JIBUN_ADDRESS              VARCHAR2(4000),
    ROAD_ADDRESS               VARCHAR2(4000),
    BUSINESS_NAME              VARCHAR2(4000),
    COORD_X                    NUMBER,
    COORD_Y                    NUMBER,
    DISTRICT_NAME              VARCHAR2(4000),
    GEOCODING_STATUS           VARCHAR2(4000),
    GEOCODING_ADDRESS_TYPE     VARCHAR2(4000),
    API_RESPONSE_ADDRESS       VARCHAR2(4000)
);

CREATE SEQUENCE SEQ_ANALYSIS_KARAOKE_ROOMS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 8. ANALYSIS_DANRAN_BARS (단란주점)
--    용도: 독립변수 - 단란주점 밀도
-- ============================================================
DROP TABLE ANALYSIS_DANRAN_BARS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_DANRAN_BARS;

CREATE TABLE ANALYSIS_DANRAN_BARS (
    ID                         NUMBER,
    DISTRICT_CODE              VARCHAR2(4000),
    MANAGEMENT_NUMBER          VARCHAR2(4000),
    BUSINESS_STATUS_NAME       VARCHAR2(4000),
    PHONE_NUMBER               VARCHAR2(4000),
    JIBUN_ADDRESS              VARCHAR2(4000),
    ROAD_ADDRESS               VARCHAR2(4000),
    BUSINESS_NAME              VARCHAR2(4000),
    COORD_X                    NUMBER,
    COORD_Y                    NUMBER,
    DISTRICT_NAME              VARCHAR2(4000),
    GEOCODING_STATUS           VARCHAR2(4000),
    GEOCODING_ADDRESS_TYPE     VARCHAR2(4000),
    API_RESPONSE_ADDRESS       VARCHAR2(4000)
);

CREATE SEQUENCE SEQ_ANALYSIS_DANRAN_BARS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 9. ANALYSIS_UNIVERSITY_STATISTICS (대학교)
--    용도: 독립변수 - 고등교육기관 밀도
-- ============================================================
DROP TABLE ANALYSIS_UNIVERSITY_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_UNIVERSITY_STATISTICS;

CREATE TABLE ANALYSIS_UNIVERSITY_STATISTICS (
    ID                  NUMBER,
    SCHOOL_NAME         VARCHAR2(4000),
    SCHOOL_NAME_ENG     VARCHAR2(4000),
    MAIN_BRANCH_TYPE    VARCHAR2(4000),
    UNIVERSITY_TYPE     VARCHAR2(4000),
    SCHOOL_TYPE         VARCHAR2(4000),
    ESTABLISHMENT_TYPE  VARCHAR2(4000),
    SIDO_CODE           VARCHAR2(4000),
    SIDO_NAME           VARCHAR2(4000),
    ROAD_ADDRESS        VARCHAR2(4000),
    ROAD_POSTAL_CODE    VARCHAR2(4000),
    HOMEPAGE_URL        VARCHAR2(4000),
    MAIN_PHONE          VARCHAR2(4000),
    MAIN_FAX            VARCHAR2(4000),
    ESTABLISHMENT_DATE  DATE,
    BASE_YEAR           NUMBER,
    DATA_BASE_DATE      DATE,
    PROVIDER_CODE       VARCHAR2(4000),
    PROVIDER_NAME       VARCHAR2(4000),
    LATITUDE            NUMBER,
    LONGITUDE           NUMBER
);

CREATE SEQUENCE SEQ_ANALYSIS_UNIVERSITY_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 10. ANALYSIS_MART_STATISTICS (대형마트/백화점)
--     용도: 독립변수 - 상업시설 밀도
-- ============================================================
DROP TABLE ANALYSIS_MART_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_MART_STATISTICS;

CREATE TABLE ANALYSIS_MART_STATISTICS (
    ID                    NUMBER,
    BUSINESS_STATUS_NAME  VARCHAR2(4000),
    PHONE_NUMBER          VARCHAR2(4000),
    ADDRESS               VARCHAR2(4000),
    ROAD_ADDRESS          VARCHAR2(4000),
    BUSINESS_NAME         VARCHAR2(4000),
    BUSINESS_TYPE_NAME    VARCHAR2(4000),
    LATITUDE              NUMBER(10,7),
    LONGITUDE             NUMBER(10,7)
);

CREATE SEQUENCE SEQ_ANALYSIS_MART_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 11. ANALYSIS_RESIDENT_CENTER_STATISTICS (주민센터)
--     용도: 독립변수 - 행정시설 접근성
-- ============================================================
DROP TABLE ANALYSIS_RESIDENT_CENTER_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_RESIDENT_CENTER_STATISTICS;

CREATE TABLE ANALYSIS_RESIDENT_CENTER_STATISTICS (
    ID            NUMBER,
    SIDO          VARCHAR2(4000),
    SIGUNGU       VARCHAR2(4000),
    EUPMEONDONG   VARCHAR2(4000),
    ADDRESS       VARCHAR2(4000),
    LATITUDE      NUMBER(10,7),
    LONGITUDE     NUMBER(10,7)
);

CREATE SEQUENCE SEQ_ANALYSIS_RESIDENT_CENTER_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 12. ANALYSIS_LODGING_STATISTICS (숙박업)
--     용도: 독립변수 - 관광 인프라 밀도
-- ============================================================
DROP TABLE ANALYSIS_LODGING_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_LODGING_STATISTICS;

CREATE TABLE ANALYSIS_LODGING_STATISTICS (
    ID                       NUMBER,
    BUILDING_OWNERSHIP_TYPE  VARCHAR2(4000),
    BUSINESS_NAME            VARCHAR2(4000),
    BUSINESS_STATUS_NAME     VARCHAR2(4000),
    BUSINESS_TYPE_NAME       VARCHAR2(4000),
    DETAIL_STATUS_NAME       VARCHAR2(4000),
    FULL_ADDRESS             VARCHAR2(4000),
    HYGIENE_BUSINESS_TYPE    VARCHAR2(4000),
    ROAD_ADDRESS             VARCHAR2(4000),
    SERVICE_NAME             VARCHAR2(4000),
    LATITUDE                 NUMBER(10,7),
    LONGITUDE                NUMBER(10,7)
);

CREATE SEQUENCE SEQ_ANALYSIS_LODGING_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 13. ANALYSIS_CINEMA_STATISTICS (영화관)
--     용도: 독립변수 - 문화시설 밀도
-- ============================================================
DROP TABLE ANALYSIS_CINEMA_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_CINEMA_STATISTICS;

CREATE TABLE ANALYSIS_CINEMA_STATISTICS (
    ID                        NUMBER,
    BUSINESS_STATUS_NAME      VARCHAR2(4000),
    PHONE_NUMBER              VARCHAR2(4000),
    JIBUN_ADDRESS             VARCHAR2(4000),
    ROAD_ADDRESS              VARCHAR2(4000),
    BUSINESS_NAME             VARCHAR2(4000),
    CULTURE_SPORTS_TYPE_NAME  VARCHAR2(4000),
    BUILDING_USE_NAME         VARCHAR2(4000),
    LATITUDE                  NUMBER,
    LONGITUDE                 NUMBER
);

CREATE SEQUENCE SEQ_ANALYSIS_CINEMA_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 14. ANALYSIS_ENTERTAINMENT_STATISTICS (유흥주점)
--     용도: 독립변수 - 야간 상권 활성도
--     ★ wherehouse 배치에서 안전성 점수 계산에 직접 사용
-- ============================================================
DROP TABLE ANALYSIS_ENTERTAINMENT_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS;

CREATE TABLE ANALYSIS_ENTERTAINMENT_STATISTICS (
    ID                      NUMBER,
    BUSINESS_STATUS_NAME    VARCHAR2(4000),
    PHONE_NUMBER            VARCHAR2(4000),
    JIBUN_ADDRESS           VARCHAR2(4000),
    ROAD_ADDRESS            VARCHAR2(4000),
    BUSINESS_NAME           VARCHAR2(4000),
    BUSINESS_CATEGORY       VARCHAR2(4000),
    HYGIENE_BUSINESS_TYPE   VARCHAR2(4000),
    LATITUDE                NUMBER(10,7),
    LONGITUDE               NUMBER(10,7)
);

CREATE SEQUENCE SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 15. ANALYSIS_SUBWAY_STATION (지하철역)
--     용도: 독립변수 - 대중교통 접근성
-- ============================================================
DROP TABLE ANALYSIS_SUBWAY_STATION CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_SUBWAY_STATION;

CREATE TABLE ANALYSIS_SUBWAY_STATION (
    ID                NUMBER,
    STATION_NAME_KOR  VARCHAR2(4000),
    STATION_PHONE     VARCHAR2(4000),
    ROAD_ADDRESS      VARCHAR2(4000),
    JIBUN_ADDRESS     VARCHAR2(4000),
    LATITUDE          NUMBER(10,7),
    LONGITUDE         NUMBER(10,7)
);

CREATE SEQUENCE SEQ_ANALYSIS_SUBWAY_STATION START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 16. ANALYSIS_SCHOOL_STATISTICS (초중고등학교)
--     용도: 독립변수 - 교육시설 밀도
-- ============================================================
DROP TABLE ANALYSIS_SCHOOL_STATISTICS CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_SCHOOL_STATISTICS;

CREATE TABLE ANALYSIS_SCHOOL_STATISTICS (
    ID                     NUMBER,
    SCHOOL_ID              VARCHAR2(4000),
    SCHOOL_NAME            VARCHAR2(4000),
    SCHOOL_LEVEL           VARCHAR2(4000),
    ESTABLISHMENT_TYPE     VARCHAR2(4000),
    MAIN_BRANCH_TYPE       VARCHAR2(4000),
    OPERATION_STATUS       VARCHAR2(4000),
    LOCATION_ADDRESS       VARCHAR2(4000),
    ROAD_ADDRESS           VARCHAR2(4000),
    EDUCATION_OFFICE_NAME  VARCHAR2(4000),
    SUPPORT_OFFICE_NAME    VARCHAR2(4000),
    LATITUDE               NUMBER(10,7),
    LONGITUDE              NUMBER(10,7),
    PROVIDER_NAME          VARCHAR2(4000)
);

CREATE SEQUENCE SEQ_ANALYSIS_SCHOOL_STATISTICS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 17. ANALYSIS_CONVENIENCE_STORE_DATA (편의점)
--     용도: 독립변수 - 상권 밀도
-- ============================================================
DROP TABLE ANALYSIS_CONVENIENCE_STORE_DATA CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_CONVENIENCE_STORE_DATA;

CREATE TABLE ANALYSIS_CONVENIENCE_STORE_DATA (
    ID                    NUMBER,
    BUSINESS_NAME         VARCHAR2(4000),
    DETAILED_STATUS_NAME  VARCHAR2(4000),
    PHONE_NUMBER          VARCHAR2(4000),
    LOT_ADDRESS           VARCHAR2(4000),
    ROAD_ADDRESS          VARCHAR2(4000),
    LATITUDE              NUMBER(10,7),
    LONGITUDE             NUMBER(10,7)
);

CREATE SEQUENCE SEQ_ANALYSIS_CONVENIENCE_STORE_DATA START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 18. ANALYSIS_POPULATION_DENSITY (인구밀도)
--     용도: 독립변수 - 인구 밀도
--     ★ wherehouse 배치에서 안전성 점수 계산에 직접 사용
-- ============================================================
DROP TABLE ANALYSIS_POPULATION_DENSITY CASCADE CONSTRAINTS PURGE;
DROP SEQUENCE SEQ_ANALYSIS_POPULATION_DENSITY;

CREATE TABLE ANALYSIS_POPULATION_DENSITY (
    ID                   NUMBER,
    DISTRICT_NAME        VARCHAR2(100),
    YEAR                 NUMBER,
    POPULATION_COUNT     NUMBER,
    AREA_SIZE            NUMBER(15,5),
    POPULATION_DENSITY   NUMBER(15,5)
);

CREATE SEQUENCE SEQ_ANALYSIS_POPULATION_DENSITY START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 검증 쿼리: 전체 테이블 및 시퀀스 생성 확인
-- ============================================================
SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'ANALYSIS_%' ORDER BY TABLE_NAME;
SELECT SEQUENCE_NAME FROM USER_SEQUENCES WHERE SEQUENCE_NAME LIKE 'SEQ_ANALYSIS_%' ORDER BY SEQUENCE_NAME;

-- ============================================================
-- 참고: wherehouse 배치 스케줄러에서 실제 사용하는 핵심 3개 테이블
-- ============================================================
-- 1. ANALYSIS_CRIME_STATISTICS       → crimeRepository.findCrimeCount()
-- 2. ANALYSIS_POPULATION_DENSITY     → populationRepository.findPopulationCountByDistrict()
-- 3. ANALYSIS_ENTERTAINMENT_STATISTICS → entertainmentRepository.findActiveEntertainmentCountByDistrict()
--
-- 나머지 15개 테이블은 AnalyzeSafetyScore 프로젝트의
-- 피어슨 상관분석/다중회귀분석에서 사용되며,
-- 향후 안전성 점수 계산 공식 고도화 시 활용 가능합니다.
-- ============================================================
