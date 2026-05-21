package com.wherehouse.VisitReservation.entity;

/**
 * 방문 슬롯 상태 Enum (설계 명세서 섹션 5.1, 5.2).
 *
 * 슬롯의 생애주기 상태를 표현한다. RDB 컬럼 VISIT_SLOT.STATUS VARCHAR2(15)
 * NOT NULL 에 대문자 영문 문자열로 저장된다.
 *
 * 상태 전이 규칙 (섹션 5.2):
 *   AVAILABLE → RESERVED  (T-S1 슬롯 예약 확정)
 *   AVAILABLE → CLOSED    (T-S2 시간 경과, 예약 없음)
 *   AVAILABLE → WITHDRAWN (T-S3 윈도우 철회)
 *   RESERVED  → AVAILABLE (T-S4 예약 취소)
 *   RESERVED  → CLOSED    (T-S5 시간 경과, 확정 예약 존재)
 *   RESERVED  → WITHDRAWN (T-S6 윈도우 철회, 예약 무효화 동반)
 *
 * 위 외 모든 전이는 금지. CLOSED 와 WITHDRAWN 은 최종 상태.
 */
public enum VisitSlotStatus {

    /** 예약 가능. 윈도우 공개 시 슬롯의 초기 상태. */
    AVAILABLE,

    /** 한 탐색자에게 확정되어 다른 탐색자가 예약할 수 없는 상태. */
    RESERVED,

    /** 슬롯의 예정 시간이 지난 종료 상태. 방문 결과는 연결 예약의 VISIT_RESULT 로 구분. */
    CLOSED,

    /** 등록자의 윈도우 철회로 비활성화된 최종 상태. */
    WITHDRAWN
}
