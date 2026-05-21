package com.wherehouse.VisitReservation.entity;

/**
 * 방문 윈도우 상태 Enum (설계 명세서 섹션 4.1.1).
 *
 * VISIT_WINDOW.STATUS VARCHAR2(10) CHECK IN ('ACTIVE','WITHDRAWN') 에 저장된다.
 *
 * 상태 전이는 단방향 1 회뿐이다:
 *   ACTIVE → WITHDRAWN  (F002 윈도우 철회 또는 매물 비활성화 연동 일괄 철회)
 *
 * 기획서 정의(섹션 7 비즈니스 규칙)에 따라 윈도우 철회는 되돌릴 수 없다. 변경이
 * 필요하면 철회 후 신규 공개로 처리한다. 따라서 WITHDRAWN 은 최종 상태.
 */
public enum VisitWindowStatus {

    /** 활성. 공개된 상태로 소속 슬롯이 예약 대상이 된다. */
    ACTIVE,

    /** 철회. 소속 슬롯이 모두 WITHDRAWN 으로 전이되고 확정 예약은 무효화된 최종 상태. */
    WITHDRAWN
}
