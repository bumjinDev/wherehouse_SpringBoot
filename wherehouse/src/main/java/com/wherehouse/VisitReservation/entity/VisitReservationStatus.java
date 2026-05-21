package com.wherehouse.VisitReservation.entity;

/**
 * 방문 예약 상태 Enum (설계 명세서 섹션 5.3, 5.4).
 *
 * 예약의 생애주기 상태를 표현한다. RDB 컬럼 VISIT_RESERVATION.STATUS VARCHAR2(15)
 * NOT NULL 에 대문자 영문 문자열로 저장된다.
 *
 * 상태 전이 규칙 (섹션 5.4):
 *   CONFIRMED → CANCELLED    (T-R1 탐색자 취소)
 *   CONFIRMED → INVALIDATED  (T-R2 윈도우 철회로 무효화)
 *   CONFIRMED → COMPLETED    (T-R3 슬롯 시간 경과)
 *
 * CANCELLED, INVALIDATED, COMPLETED 는 최종 상태.
 *
 * 데이터베이스 차원 무결성 백스톱 (섹션 4.1.3):
 *   UQ_VISIT_RESERVATION_CONFIRMED_SLOT 부분 유일 인덱스가 한 슬롯에
 *   STATUS=CONFIRMED 행을 최대 1건으로 강제한다. 그 외 상태(CANCELLED·
 *   INVALIDATED·COMPLETED)는 슬롯당 누적 가능.
 */
public enum VisitReservationStatus {

    /** 확정. 활성 예약 상태로 슬롯 방문 시각에 탐색자가 방문할 것으로 기대됨. */
    CONFIRMED,

    /** 탐색자가 직접 취소한 상태. */
    CANCELLED,

    /** 등록자의 윈도우 철회로 강제 무효화된 상태. */
    INVALIDATED,

    /** 슬롯의 예정 시간이 지나 종료된 상태. 방문 결과는 VISIT_RESULT 로 구분. */
    COMPLETED
}
