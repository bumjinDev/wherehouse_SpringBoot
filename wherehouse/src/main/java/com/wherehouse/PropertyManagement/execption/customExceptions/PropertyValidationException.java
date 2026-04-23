package com.wherehouse.PropertyManagement.execption.customExceptions;

/**
 * 서비스 계층 2차 검증 실패 예외 (400 Bad Request, E4001).
 *
 * 발생 상황:
 *   - 서울시 25개 자치구 코드 화이트리스트 위반
 *   - 임대 유형별 조건부 필드 정합성 위반 (전세에 monthlyRent, 월세에 monthlyRent 누락)
 *   - 기타 DTO 단계 Bean Validation 으로 표현 불가한 2차 검증 실패
 *
 * Bean Validation(@Valid) 실패는 MethodArgumentNotValidException 으로 별도 처리되며
 * 본 예외와 구분된다. 외부 응답 코드는 동일 E4001 이나 내부 로그에서 구분 가능.
 */
public class PropertyValidationException extends RuntimeException {

    public PropertyValidationException(String message) {
        super(message);
    }

    public PropertyValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}