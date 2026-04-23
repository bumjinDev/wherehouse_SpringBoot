package com.wherehouse.PropertyManagement.execption.customExceptions;

/**
 * 종료 상태(COMPLETED/DELETED) 매물 수정 시도 시 발생하는 예외 (409 Conflict, E4106).
 *
 * 설계 명세서 참조:
 *   - 섹션 6.4: 매물 상태별 동작 규칙 — COMPLETED/DELETED 상태의 매물은 F002 수정 불가
 *   - 섹션 7.8: 에러 코드 E4106 → HTTP 409 Conflict
 *   - R-F002-04: "종료 상태 수정 차단 — 현재 상태가 COMPLETED이면 E4106. ACTIVE만 수정 허용"
 *   - 섹션 9.2.3: "종료 상태 수정 검증이 3단계 이후에 수행되는 근거 — COMPLETED 상태라 수정 불가는
 *                권한 검증이 아닌 상태 조건 검증. 권한 없는 사용자에게 종료 상태 정보가 노출되면
 *                안 되므로 권한 검증이 선행된다"
 *
 * 발생 상황:
 *   - F002 매물 수정 시 권한 검증(3단계)은 통과했으나 현재 매물 상태가 COMPLETED 또는 DELETED
 *
 * 설계 의도 — UnauthorizedPropertyAccessException 과의 구분:
 *   권한 검증 실패(E4003)와 상태 조건 검증 실패(E4106)는 사용자 관점에서 다른 의미를 가진다.
 *   전자는 "이 매물에 접근할 자격이 없음", 후자는 "본인 매물이나 상태상 수정 불가능"이다.
 *   이 구분은 UI가 사용자에게 적절한 안내 메시지를 표시하는 근거가 된다.
 *
 * 에러 코드 매핑: GlobalExceptionHandlerProperty 에서 E4106으로 매핑.
 */
public class InvalidStateForUpdateException extends RuntimeException {

    public InvalidStateForUpdateException(String message) {
        super(message);
    }

    public InvalidStateForUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
