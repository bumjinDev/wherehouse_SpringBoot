package com.wherehouse.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 게시글 접근 권한이 없는 경우 발생하는 예외 (HTTP 403)
 *
 * - 발생 조건: 게시글 리소스는 존재하지만, 현재 사용자가 해당 리소스에 대한 접근 권한이 없을 경우
 * - 예외 성격: 통상적인 접근 요청에 대한 권한 검증 실패로 인한 거부
 * - 목적 구분: 명시적으로 허용되지 않은 사용자의 접근 차단 (의도적 위반이 아닌 일반 인가 실패)
 * - 적용 위치: 게시글 수정/삭제 페이지 진입, 컨트롤러 접근 전 권한 검사 단계
 * - 처리 방식: HTTP 403 (Forbidden) 상태 코드 응답
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class BoardAuthorizationException extends RuntimeException {
    public BoardAuthorizationException(String message) {
        super(message);
    }
}
