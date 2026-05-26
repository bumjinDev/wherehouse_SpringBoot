--------------------------------------------------------------------------------
-- Wherehouse 매물 방문 예약 기능 — 시퀀스 생성 스크립트
-- 기준: 설계 명세서 v1.4 섹션 4.1
-- 실행 순서: 2번
-- 의존성: 시퀀스는 테이블 DDL과 독립이지만, 본 프로젝트 실행 순서상
--         01_tables.sql 이후에 실행한다. (애플리케이션 INSERT 시점에 사용)
--------------------------------------------------------------------------------

-- 윈도우 시퀀스
CREATE SEQUENCE SEQ_VISIT_WINDOW
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

-- 슬롯 시퀀스
CREATE SEQUENCE SEQ_VISIT_SLOT
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

-- 예약 시퀀스
CREATE SEQUENCE SEQ_VISIT_RESERVATION
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

-- 재개방 알림 구독 시퀀스
CREATE SEQUENCE SEQ_REOPEN_SUBSCRIPTION
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;

-- 알림 시퀀스
CREATE SEQUENCE SEQ_VISIT_NOTIFICATION
    START WITH 1
    INCREMENT BY 1
    CACHE 20
    NOCYCLE;
