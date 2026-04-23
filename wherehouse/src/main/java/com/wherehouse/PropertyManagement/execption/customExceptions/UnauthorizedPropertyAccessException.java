package com.wherehouse.PropertyManagement.execption.customExceptions;

/**
 * 매물 접근 권한 없음 시 발생하는 예외 (403 Forbidden, E4003).
 *
 * 설계 명세서 참조:
 *   - 섹션 7.8: 에러 코드 E4003 → HTTP 403 Forbidden
 *   - 섹션 9.2.3: 권한 검증 3단계 중 2·3단계 실패 시점에 발생
 *   - 섹션 9.2.3 근거: "두 실패(등록자 부재, 본인 불일치)가 사용자 관점에서 동일 의미(권한 없음)를
 *                     가지며 배치 매물 여부를 에러 응답에 노출하면 내부 데이터 출처 정보가 유출되는
 *                     부작용이 있기 때문"에 외부 코드를 통합.
 *
 * 발생 상황:
 *   1) 2단계 실패 — REGISTERED_USER_ID 컬럼 값이 NULL (배치 매물 수정 시도)
 *   2) 3단계 실패 — REGISTERED_USER_ID 값이 인증 컨텍스트의 현재 사용자 식별자와 불일치
 *
 * 내부 로그에는 실패 원인을 구분 기록하여 로그 분석을 지원하되,
 * 외부 응답 메시지는 단일화하여 내부 정보 유출을 방지한다.
 *
 * 에러 코드 매핑: GlobalExceptionHandlerProperty 에서 E4003으로 매핑.
 */
public class UnauthorizedPropertyAccessException extends RuntimeException {

    public UnauthorizedPropertyAccessException(String message) {
        super(message);
    }

    public UnauthorizedPropertyAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
