--------------------------------------------------------------------------------
-- Wherehouse 매물 방문 예약 기능 — 추가 유일 제약 스크립트 (무결성 백스톱)
-- 기준: 설계 명세서 v1.4 섹션 4.1.2 / 4.1.3 / 4.1.4
-- 실행 순서: 3번
-- 의존성: 01_tables.sql 실행 완료
-- 목적: 동시성 제어 기법과 직교(直交)하여 동작하는, 데이터베이스 차원의
--       마지막 무결성 방어선을 설정한다. 어떤 동시성 제어 기법을 통과한
--       INSERT/UPDATE도 본 제약을 마지막에 한 번 더 통과해야 한다.
--------------------------------------------------------------------------------


--==============================================================================
-- 1) UQ_VISIT_SLOT_WINDOW_START
--    윈도우 내 시작 시각 유일 제약
--    같은 윈도우에 같은 시작 시각의 슬롯이 두 번 INSERT 되는 것을 차단
--    (설계 명세서 4.1.2)
--==============================================================================
ALTER TABLE VISIT_SLOT
    ADD CONSTRAINT UQ_VISIT_SLOT_WINDOW_START
        UNIQUE (WINDOW_ID, START_TIME);


--==============================================================================
-- 2) UQ_VISIT_RESERVATION_CONFIRMED_SLOT
--    슬롯당 확정 예약 1건 강제 (부분 유일 제약 — Function-Based Unique Index)
--
--    STATUS = 'CONFIRMED'인 행만 인덱스 키(SLOT_ID)를 가지며, 그 외 상태의 행은
--    NULL 키가 되어 제약에서 빠진다. 따라서 한 슬롯에 여러 예약 행이 누적되어도
--    어느 시점에나 CONFIRMED 행은 슬롯당 최대 1건임이 데이터베이스 차원에서
--    보장된다.
--    (설계 명세서 4.1.3)
--==============================================================================
CREATE UNIQUE INDEX UQ_VISIT_RESERVATION_CONFIRMED_SLOT
    ON VISIT_RESERVATION (
        CASE WHEN STATUS = 'CONFIRMED' THEN SLOT_ID END
    );


--==============================================================================
-- 3) UQ_REOPEN_SUBSCRIPTION_ACTIVE
--    한 탐색자의 슬롯당 활성 구독 중복 방지
--    (부분 유일 제약 — Function-Based Unique Index, 2-컬럼)
--
--    STATUS = 'ACTIVE'인 행에만 (SLOT_ID, SEARCHER_USER_ID) 유일성을 적용한다.
--    종료된 구독(CANCELLED/FULFILLED/EXPIRED) 행은 두 표현식 모두 NULL이 되어
--    인덱스에서 제외되므로, 같은 탐색자-슬롯 쌍에 시간에 걸쳐 종료 행이
--    누적될 수 있다(구독 → 해제 → 재구독 흐름 허용).
--    (설계 명세서 4.1.4)
--==============================================================================
CREATE UNIQUE INDEX UQ_REOPEN_SUBSCRIPTION_ACTIVE
    ON REOPEN_SUBSCRIPTION (
        CASE WHEN STATUS = 'ACTIVE' THEN SLOT_ID END,
        CASE WHEN STATUS = 'ACTIVE' THEN SEARCHER_USER_ID END
    );
