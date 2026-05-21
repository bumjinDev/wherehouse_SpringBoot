package com.wherehouse.VisitReservation.entity;

/**
 * 임대 유형 Enum — 방문 예약 도메인 (설계 명세서 섹션 4.1.1).
 *
 * VISIT_WINDOW.LEASE_TYPE VARCHAR2(10) CHECK IN ('CHARTER','MONTHLY') 에
 * 대문자 영문 문자열로 저장된다. 매물의 두 임대 테이블(PROPERTIES_CHARTER /
 * PROPERTIES_MONTHLY) 중 어느 쪽을 참조하는 윈도우인지를 식별한다.
 *
 * 기존 매물 도메인과의 표현 차이 (의도된 분리):
 *   PROPERTIES_CHARTER / PROPERTIES_MONTHLY 의 LEASE_TYPE 컬럼은 한글 "전세"/"월세"
 *   로 저장된다(기존 관례). 본 도메인의 VISIT_WINDOW.LEASE_TYPE 는 영문 코드를
 *   저장하며, 양쪽이 직접 동치 비교되지 않는다. 한 쌍 ⟨PROPERTY_ID, LEASE_TYPE⟩ 이
 *   본 도메인 안에서 매물을 유일 식별한다(섹션 4.1.1).
 */
public enum LeaseType {

    /** 전세. PROPERTIES_CHARTER 테이블 참조. */
    CHARTER,

    /** 월세. PROPERTIES_MONTHLY 테이블 참조. */
    MONTHLY
}
