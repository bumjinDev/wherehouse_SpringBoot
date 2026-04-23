package com.wherehouse.PropertyManagement.execption.customExceptions;

/**
 * 허용되지 않는 상태 전이 요청 시 발생하는 예외 (400 Bad Request, E4002).
 *
 * 설계 명세서 참조:
 *   - 섹션 6.2: 매물 상태 전이 규칙 — ACTIVE 상태에서만 출발하는 단방향 전이로 한정
 *   - 섹션 7.8: 에러 코드 E4002 → HTTP 400 Bad Request
 *   - 섹션 9.3.4: 상태 전이 허용성 검증 — 권한 검증 통과 직후, 매물 상태 갱신 쿼리 발행 직전(R-F003-04)
 *
 * 허용 전이 (섹션 6.2):
 *   초기 → ACTIVE       (F001 매물 등록)
 *   ACTIVE → COMPLETED  (F003 거래완료)
 *   ACTIVE → DELETED    (F003 삭제)
 *
 * 금지 전이 (섹션 6.2):
 *   COMPLETED → ACTIVE    — 거래 체결 취소는 본 설계 범위 외
 *   DELETED → ACTIVE      — 동일 근거
 *   COMPLETED → DELETED   — 두 종료 상태 간 전환은 의미 중복
 *   DELETED → COMPLETED   — 동일 근거
 *   ACTIVE → ACTIVE       — 상태 전이가 아닌 가변 속성 수정은 F002에서 처리
 *
 * 발생 상황:
 *   - F003 매물 상태 변경 요청에서 targetStatus 값이 현재 상태로부터 허용되지 않는 전이인 경우
 *   - 예: COMPLETED 매물에 대해 다시 COMPLETED 또는 ACTIVE로 변경 요청
 *
 * E4001(유효성 검증)과의 차이:
 *   유효성 검증은 요청 값 자체의 형식 적합성 판단(예: targetStatus가 ACTIVE/COMPLETED/DELETED 중 하나인지).
 *   본 예외는 요청 값과 현재 상태의 조합이 허용되는지 판단(예: COMPLETED 매물에 DELETED 전이 요청 불허).
 *
 * 에러 코드 매핑: GlobalExceptionHandlerProperty 에서 E4002로 매핑.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
