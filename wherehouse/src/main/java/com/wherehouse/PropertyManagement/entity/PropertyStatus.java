package com.wherehouse.PropertyManagement.entity;

/**
 * 매물 상태 Enum (설계 명세서 섹션 6.1, 8.3).
 *
 * 시스템이 관리하는 매물의 생애주기 상태를 표현한다.
 * RDB 컬럼(STATUS VARCHAR2(10) DEFAULT 'ACTIVE' NOT NULL), Redis Hash 필드,
 * API 요청·응답 JSON 에서 모두 동일한 대문자 영문 문자열로 표현된다.
 *
 * 상태 정의 (섹션 6.1):
 *   ACTIVE    — 거래 진행 중 매물. 등록 직후의 기본 상태.
 *               검색 인덱스 포함, 등록자 본인 수정·전이 가능.
 *   COMPLETED — 거래 체결 매물. 데이터 보존, 리뷰 열람용 개별 조회 허용.
 *               검색 인덱스 제외, 수정·전이 불가.
 *   DELETED   — 등록자가 삭제 처리한 매물. 개별 조회도 미존재 응답.
 *               검색 인덱스 제외, 수정·전이 불가.
 *
 * 상태 전이 규칙 (섹션 6.2):
 *   초기 → ACTIVE         (F001 매물 등록)
 *   ACTIVE → COMPLETED    (F003 거래완료)
 *   ACTIVE → DELETED      (F003 삭제)
 *   그 외 모든 전이 금지 — 금지 전이 시 InvalidStatusTransitionException(E4002) 발생
 *
 * Enum 값 표기 일관성 (섹션 8.3):
 *   RDB·Redis·API 응답·내부 Enum 의 네 표현 계층 모두에서 대문자 영문 문자열 사용.
 *   한글 표기는 기존 LEASE_TYPE 컬럼에 한정되며 본 고도화 신규 Enum 에는 미적용.
 *
 * 구현 주의 — Redis 표현의 비대칭성:
 *   COMPLETED 는 매물 Hash 의 status 필드에 "COMPLETED" 문자열로 관찰됨.
 *   DELETED 는 매물 Hash 자체가 제거되므로 status 필드로 "DELETED" 가 관찰되지 않음(섹션 8.3).
 *   이 비대칭은 Enum 정의가 아닌 동기화 동작(F008)의 설계 결정이며 본 Enum 은 세 값을 모두 선언.
 */
public enum PropertyStatus {

    /**
     * 거래 진행 중. 등록 직후 기본 상태, 검색 노출, 수정·전이 가능.
     */
    ACTIVE,

    /**
     * 거래 체결. 검색 제외, 데이터 보존, 수정·전이 불가.
     * 리뷰 열람을 위한 매물 상세 조회는 허용(섹션 6.4).
     */
    COMPLETED,

    /**
     * 등록자 삭제. 검색 제외, 매물 상세 조회도 404 반환.
     * RDB 레코드는 보존되어 리뷰 참조 무결성 유지(섹션 6.3).
     */
    DELETED
}