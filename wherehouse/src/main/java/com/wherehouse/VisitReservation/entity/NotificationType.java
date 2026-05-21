package com.wherehouse.VisitReservation.entity;

/**
 * 방문 예약 알림 유형 Enum (설계 명세서 섹션 4.1.5, 2.5).
 *
 * VISIT_NOTIFICATION.NOTIFICATION_TYPE VARCHAR2(30) NOT NULL 에 저장된다.
 *
 * 발생 맥락:
 *   SLOT_RESERVED            — 등록자의 슬롯이 예약되었음을 등록자에게 통지 (F004)
 *   RESERVATION_INVALIDATED  — 등록자의 윈도우 철회로 탐색자의 확정 예약이 무효화 (F002)
 *   SLOT_REOPENED            — 구독 중인 슬롯이 예약 취소로 다시 열림 (F005)
 *   PROPERTY_DEACTIVATED     — 매물 비활성화로 탐색자의 확정 예약이 무효화 (매물 상태 연동)
 *
 * 행 생성 규칙 (섹션 4.1.5):
 *   하나의 사건이 여러 수신자에게 영향을 주면 수신자마다 한 행이 생성된다.
 *   PROPERTY_DEACTIVATED 는 한 매물 비활성화 사건이 무효화된 확정 예약 수만큼
 *   행으로 분할되며, 동일 매물 중복 예약 금지 규칙으로 인해 한 탐색자에게
 *   생성되는 본 유형의 알림은 정확히 1 건이다.
 */
public enum NotificationType {

    /** 등록자의 슬롯이 예약됨. 수신자=등록자. */
    SLOT_RESERVED,

    /** 윈도우 철회로 탐색자의 확정 예약이 무효화됨. 수신자=탐색자. */
    RESERVATION_INVALIDATED,

    /** 구독 중인 슬롯이 다시 열림. 수신자=활성 구독자. */
    SLOT_REOPENED,

    /** 매물 비활성화 연동으로 윈도우가 일괄 철회되어 확정 예약이 무효화됨. 수신자=탐색자. */
    PROPERTY_DEACTIVATED
}
