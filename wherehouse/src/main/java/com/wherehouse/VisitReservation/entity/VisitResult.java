package com.wherehouse.VisitReservation.entity;

/**
 * 방문 결과 Enum (설계 명세서 섹션 5.1 종료 상태 세부 구분, 5.4).
 *
 * 슬롯이 종료된 후 등록자가 분류하는 방문 결과를 표현한다. RDB 컬럼
 * VISIT_RESERVATION.VISIT_RESULT VARCHAR2(10) 에 저장되며, 분류 전에는 NULL
 * (미분류) 이다. 본 값은 예약 STATUS=COMPLETED 일 때만 의미를 가진다.
 *
 * 종료된 슬롯의 4 가지 세부 해석 (섹션 5.1):
 *   STATUS=COMPLETED, VISIT_RESULT=VISITED  → 방문 완료
 *   STATUS=COMPLETED, VISIT_RESULT=NO_SHOW  → 노쇼
 *   STATUS=COMPLETED, VISIT_RESULT=NULL     → 종료 (미분류)
 *   확정 예약 자체가 존재하지 않음           → 예약 없는 종료
 *
 * 본 Enum 은 분류된 두 결과 값만 표현하며, 미분류 상태는 컬럼 NULL 로 표현된다.
 */
public enum VisitResult {

    /** 방문 완료. 탐색자가 실제로 방문한 것으로 등록자가 분류한 결과. */
    VISITED,

    /** 노쇼. 탐색자가 약속한 슬롯 시간에 방문하지 않은 것으로 등록자가 분류한 결과. */
    NO_SHOW
}
