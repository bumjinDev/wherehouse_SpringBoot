package com.wherehouse.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 존재하지 않는 게시글에 대해 클라이언트가 비정상적인 접근을 시도했을 때 발생하는 예외 (HTTP 404)
 *
 * - 발생 조건: DB에서 게시글 ID로 조회한 결과가 null일 경우 (즉, 게시글이 존재하지 않음)
 * - 예외 의미: 클라이언트가 허용되지 않은 경로 또는 위조된 요청 흐름을 통해 유효하지 않은 게시글에 접근을 시도한 상황
 * - 정책적 구분: 단순 리소스 없음(NotFound)을 넘어서, 의도된 잘못된 접근이라는 맥락에서 예외 처리
 * - 적용 위치: 게시글 수정/삭제 페이지 진입 또는 수정 API 요청 전 게시글 존재 여부 검증 단계
 * - 처리 방식: HTTP 404 (Not Found) 상태 코드와 함께 JSON 기반 에러 메시지 응답
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvalidBoardFoundAttemptException extends RuntimeException {
    public InvalidBoardFoundAttemptException(String message) {
        super(message);
    }
}
