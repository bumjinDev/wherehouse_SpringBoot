package com.wherehouse.VisitReservation.entity;

/**
 * 재개방 알림 구독 상태 Enum (설계 명세서 섹션 4.1.4, 6.6).
 *
 * REOPEN_SUBSCRIPTION.STATUS VARCHAR2(15) CHECK IN ('ACTIVE','FULFILLED',
 * 'CANCELLED','EXPIRED') 에 저장된다.
 *
 * 종료 전이 규칙 (기획서 9 절 + 섹션 6.6):
 *   ACTIVE → FULFILLED  (구독자가 해당 슬롯을 다시 예약하는 데 성공 — 슬롯 예약 처리)
 *   ACTIVE → CANCELLED  (구독자가 직접 해제 — 구독 해제 처리)
 *   ACTIVE → EXPIRED    (슬롯이 재개방 없이 종료 상태에 도달 — 슬롯 종료 처리)
 *
 * 데이터베이스 차원 무결성 백스톱 (섹션 4.1.4):
 *   UQ_REOPEN_SUBSCRIPTION_ACTIVE 부분 유일 인덱스가 (SLOT_ID, SEARCHER_USER_ID)
 *   에 대해 STATUS=ACTIVE 행이 동시에 1 건만 존재함을 강제한다. 종료 상태 행은
 *   누적 가능 (구독 → 해제 → 재구독 → 해제 흐름 허용).
 */
public enum SubscriptionStatus {

    /** 활성. 슬롯 재개방 시 알림을 수신한다. */
    ACTIVE,

    /** 구독자가 해당 슬롯을 다시 예약하는 데 성공해 종료. */
    FULFILLED,

    /** 구독자가 직접 해제하여 종료. */
    CANCELLED,

    /** 슬롯이 재개방 없이 종료 상태에 도달해 폐기. */
    EXPIRED
}
