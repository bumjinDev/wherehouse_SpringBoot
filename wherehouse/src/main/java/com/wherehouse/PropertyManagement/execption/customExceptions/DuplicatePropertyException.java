package com.wherehouse.PropertyManagement.execption.customExceptions;

/**
 * 중복 매물 등록 시도 시 발생하는 예외 (409 Conflict, E4105).
 *
 * 설계 명세서 참조:
 *   - 섹션 7.8: 에러 코드 E4105 → HTTP 409 Conflict
 *   - 섹션 9.1.2 R-F001-04: "중복 감지 통과 시 계속 진행, 중복 존재 시 E4105"
 *   - F006 중복 감지 (요구사항 명세서 F006): MD5 해시 기반 동일 매물 판별
 *
 * 발생 상황:
 *   F001 매물 등록 요청 시, 입력된 불변 속성 5개(시군구코드·지번·아파트명·층·전용면적)로 생성한
 *   MD5 해시가 이미 존재하는 매물의 식별자와 동일한 경우 발생.
 *
 * 중복 케이스 분류 (요구사항 명세서 F006 시나리오):
 *   Case 1: 기존 배치 매물과 중복 — "이 매물은 국토교통부 실거래 데이터에 이미 등록된 매물입니다"
 *   Case 2: 기존 사용자 등록 매물과 중복 — "동일한 매물이 다른 사용자에 의해 이미 등록되어 있습니다"
 *   Case 3: 복수 사용자 동시 등록 Race Condition — 선착순으로 하나만 허용, 후착은 본 예외 반환
 *
 * 본 예외 클래스의 인터페이스 경계 (섹션 5.2):
 *   F006 후속 설계의 DuplicateChecker 인터페이스가 중복 감지 결과로 본 예외를 던지는 계약.
 *   본 설계 범위(F001~F005)에서는 경계만 정의하며, 구체 락 전략(SELECT FOR UPDATE, 낙관적 락,
 *   PK 유니크 제약 활용 등)은 F006 후속 설계에서 확정된다.
 *
 * 에러 코드 매핑: GlobalExceptionHandlerProperty 에서 E4105로 매핑.
 */
public class DuplicatePropertyException extends RuntimeException {

    public DuplicatePropertyException(String message) {
        super(message);
    }

    public DuplicatePropertyException(String message, Throwable cause) {
        super(message, cause);
    }
}
