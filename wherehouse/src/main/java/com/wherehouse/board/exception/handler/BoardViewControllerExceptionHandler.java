package com.wherehouse.board.exception.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.wherehouse.board.exception.BoardAuthorizationException;
import com.wherehouse.board.exception.BoardNotFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * 게시판 도메인 전용 전역 예외 처리 핸들러
 *
 * - 대상: com.wherehouse.board 패키지 하위 클래스(@RestController, @Controller 포함)
 * - 목적: 게시글, 댓글 처리 중 발생하는 커스텀 예외를 일관된 JSON 형식으로 반환
 */
@Order(1)
@ControllerAdvice(basePackages = "com.wherehouse.board.controller")
public class BoardViewControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BoardViewControllerExceptionHandler.class);

    /**
     * 403 Forbidden – 게시글 작성자가 아닌 경우 인가 실패 처리
     */
    @ExceptionHandler(BoardAuthorizationException.class)
    public ResponseEntity<?> handleBoardAuthorizationException(BoardAuthorizationException ex) {
        logger.warn("BoardAuthorizationException 발생: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * 404 Not Found – 게시글이 존재하지 않는 경우
     * 게시글 조회에도 사용되지만 삭제 시에도 공통으로 적용
     */
    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<?> handleBoardNotFoundException(BoardNotFoundException ex) {
        logger.warn("BoardNotFoundException 발생: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 공통 오류 응답 JSON 구조 생성 메서드
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("code", status.value());
        errorBody.put("status", status.getReasonPhrase());
        errorBody.put("message", message);
        return new ResponseEntity<>(errorBody, status);
    }
}
