package com.wherehouse.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 요청된 게시글이 존재하지 않을 경우 발생하는 예외 (HTTP 404)
 *
 * - 발생 조건: 클라이언트가 요청한 게시글 ID에 해당하는 리소스가 DB에 존재하지 않을 경우
 * - 예외 성격: 통상적인 리소스 요청 흐름에서 발생하는 검증 실패
 * - 목적 구분: 인가 여부와 무관하게 단순히 리소스 존재 유무 판단을 위한 예외
 * - 적용 위치: 게시글 조회, 수정, 삭제 요청 시 게시글 조회 단계
 * - 처리 방식: HTTP 404 (Not Found) 상태 코드 응답
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BoardNotFoundException extends RuntimeException {
    public BoardNotFoundException(String message) {
        super(message);
    }
}
