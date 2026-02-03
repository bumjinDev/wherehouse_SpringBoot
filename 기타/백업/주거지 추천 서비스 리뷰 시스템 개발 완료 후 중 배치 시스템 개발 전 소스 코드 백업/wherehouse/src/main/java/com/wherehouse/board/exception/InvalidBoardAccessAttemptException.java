package com.wherehouse.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 권한이 없는 사용자가 게시글에 대해 비정상적인 접근을 시도했을 때 발생하는 예외 (HTTP 403)
 *
 * - 발생 조건: 게시글은 존재하지만, 현재 요청 사용자가 해당 게시글의 작성자가 아닌 경우
 * - 예외 의미: 정상적인 인증/인가 흐름을 우회하거나, 의도적으로 조작된 요청을 통해 게시글에 접근하려는 시도
 * - 정책적 구분: 단순 입력 오류가 아닌, 명시적으로 허용되지 않은 행위로 간주되는 접근 위반
 * - 적용 위치: 게시글 수정/삭제 요청 시, 작성자 ID 검증 단계에서 불일치 시 발생
 * - 처리 방식: HTTP 403 (Forbidden) 상태 코드와 함께 JSON 기반 에러 메시지 응답
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvalidBoardAccessAttemptException extends RuntimeException {
    public InvalidBoardAccessAttemptException(String message) {
        super(message);
    }
}
