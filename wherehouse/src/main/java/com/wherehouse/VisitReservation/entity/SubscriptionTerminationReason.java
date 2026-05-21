package com.wherehouse.VisitReservation.entity;

/**
 * 재개방 알림 구독 종료 사유 Enum (설계 명세서 섹션 4.1.4).
 *
 * REOPEN_SUBSCRIPTION.TERMINATION_REASON VARCHAR2(20) 에 저장되며, 구독이
 * 활성 상태에서 종료 상태로 전이될 때 함께 기록된다. 활성 상태(ACTIVE)에서는
 * NULL.
 *
 * SubscriptionStatus 의 종료 값과의 대응:
 *   SLOT_CLOSED     ↔ EXPIRED    (슬롯이 재개방 없이 종료)
 *   RESERVED        ↔ FULFILLED  (구독자의 재예약 성공)
 *   USER_CANCELLED  ↔ CANCELLED  (구독자의 직접 해제)
 *
 * 두 컬럼을 분리한 이유는, STATUS 가 도메인 상태 머신의 위치를 표현하고
 * TERMINATION_REASON 이 그 전이의 사유를 기록하는 사후 메타데이터이기 때문이다.
 * STATUS 만으로도 사유는 일대일 대응되지만, 명시적 기록을 통해 로그 분석과
 * 통계 집계의 가독성을 높인다.
 */
public enum SubscriptionTerminationReason {

    /** 슬롯이 재개방 없이 종료 상태에 도달했다. */
    SLOT_CLOSED,

    /** 구독자가 해당 슬롯을 다시 예약하는 데 성공했다. */
    RESERVED,

    /** 구독자가 직접 구독을 해제했다. */
    USER_CANCELLED
}
