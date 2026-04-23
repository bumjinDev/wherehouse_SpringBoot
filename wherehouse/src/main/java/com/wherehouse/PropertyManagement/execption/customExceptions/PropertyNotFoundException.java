package com.wherehouse.PropertyManagement.execption.customExceptions;

/**
 * 매물 미존재 또는 DELETED 상태 접근 시 발생하는 예외 (404 Not Found, E4201).
 *
 * 설계 명세서 참조:
 *   - 섹션 6.4: DELETED 상태는 "존재하지 않음"과 동일 취급 (매물 Hash 전면 제거, 404 응답)
 *   - 섹션 7.8: 에러 코드 E4201 → HTTP 404 Not Found
 *   - 섹션 9.2.3: 권한 검증 3단계의 1단계 실패 시점에 발생
 *
 * 발생 상황:
 *   1) F002 매물 수정: propertyId에 해당하는 레코드가 RDB에 부재
 *   2) F003 매물 상태 변경: 동일 (RDB 레코드 미존재)
 *   3) F004 매물 상세 조회: 미존재 또는 STATUS=DELETED 상태
 *   4) 권한 검증 3단계 중 1단계 실패 시 (섹션 9.2.3)
 *
 * 에러 코드 매핑: GlobalExceptionHandlerProperty 에서 E4201로 매핑.
 */
public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException(String message) {
        super(message);
    }

    public PropertyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
