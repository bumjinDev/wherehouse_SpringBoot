--------------------------------------------------------------------------------
-- Wherehouse 매물 방문 예약 기능 — 성능 인덱스 스크립트
-- 기준: 설계 명세서 v1.4 섹션 4.1.6
-- 실행 순서: 4번 (마지막)
-- 의존성: 01_tables.sql, 03_constraints.sql 실행 완료
-- 목적: 핵심 처리 경로의 조회 성능을 보장하기 위한 보조 인덱스.
--       동시성 제어가 임계 구간을 짧게 유지하려면 그 안에서 수행되는 조회가
--       인덱스 기반이어야 한다.
--------------------------------------------------------------------------------


--==============================================================================
-- 1) VISIT_WINDOW
--==============================================================================
-- IX_VISIT_WINDOW_PROPERTY
-- 한 매물의 활성 윈도우 조회
--  - 윈도우 공개(6.1) 시 같은 매물 활성 윈도우와의 시간 겹침 검증
--  - 슬롯 조회(6.3) 시 매물의 활성 윈도우를 먼저 찾는 첫 단계
CREATE INDEX IX_VISIT_WINDOW_PROPERTY
    ON VISIT_WINDOW (PROPERTY_ID, LEASE_TYPE, STATUS);


--==============================================================================
-- 2) VISIT_SLOT
--    UQ_VISIT_SLOT_WINDOW_START 는 03_constraints.sql 에서 이미 생성됨
--    (윈도우 식별자로 시작 시각 순 슬롯 목록 조회 용도 겸함)
--==============================================================================
-- IX_VISIT_SLOT_STATUS_END
-- 종료 시각이 지난 슬롯 식별 (슬롯 종료 처리 컴포넌트의 1분 주기 작업)
--  - 조회 조건: STATUS IN ('AVAILABLE','RESERVED') AND END_TIME < SYSTIMESTAMP
CREATE INDEX IX_VISIT_SLOT_STATUS_END
    ON VISIT_SLOT (STATUS, END_TIME);


--==============================================================================
-- 3) VISIT_RESERVATION
--    UQ_VISIT_RESERVATION_CONFIRMED_SLOT 는 03_constraints.sql 에서 이미 생성됨
--==============================================================================
-- IX_VISIT_RESERVATION_SEARCHER
-- 한 탐색자의 예약 조회
--  - 슬롯 예약(6.4) 시 동일 매물 중복 예약 검증
--  - 슬롯 예약(6.4) 시 시간 겹침 검증
--  - 탐색자 예약 현황 조회(6.8)
CREATE INDEX IX_VISIT_RESERVATION_SEARCHER
    ON VISIT_RESERVATION (SEARCHER_USER_ID, STATUS);

-- IX_VISIT_RESERVATION_SLOT
-- 한 슬롯에 묶인 예약 조회
--  - 윈도우 철회(6.2) 시 소속 슬롯들의 확정 예약을 일괄 식별해 무효화
--  - 슬롯 종료(6.7) 시 연결 예약 전이
--  - 등록자 슬롯 현황 조회(6.8)
CREATE INDEX IX_VISIT_RESERVATION_SLOT
    ON VISIT_RESERVATION (SLOT_ID, STATUS);


--==============================================================================
-- 4) REOPEN_SUBSCRIPTION
--    UQ_REOPEN_SUBSCRIPTION_ACTIVE 는 03_constraints.sql 에서 이미 생성됨
--==============================================================================
-- IX_REOPEN_SUBSCRIPTION_SLOT
-- 한 슬롯의 활성 구독자 조회 (예약 취소로 슬롯 재개방 시 알림 발송)
--  - STATUS가 두 번째 컬럼이라 인덱스 단계에서 활성 구독만 추려진다.
CREATE INDEX IX_REOPEN_SUBSCRIPTION_SLOT
    ON REOPEN_SUBSCRIPTION (SLOT_ID, STATUS);

-- IX_REOPEN_SUBSCRIPTION_SEARCHER
-- 한 탐색자의 구독 현황 조회 (6.8)
CREATE INDEX IX_REOPEN_SUBSCRIPTION_SEARCHER
    ON REOPEN_SUBSCRIPTION (SEARCHER_USER_ID, STATUS);


--==============================================================================
-- 5) VISIT_NOTIFICATION
--==============================================================================
-- IX_VISIT_NOTIFICATION_USER
-- 이용자의 미읽음 알림을 최신순으로 조회 (7.12 알림 조회 API)
--  - USER_ID로 본인 알림을 좁힘
--  - IS_READ로 미읽음 우선 정렬
--  - CREATED_AT DESC로 최신순 정렬
CREATE INDEX IX_VISIT_NOTIFICATION_USER
    ON VISIT_NOTIFICATION (USER_ID, IS_READ, CREATED_AT DESC);
