package com.wherehouse.board.exception.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.wherehouse.board.exception.BoardAuthorizationException;
import com.wherehouse.board.exception.BoardNotFoundException;
import com.wherehouse.board.exception.InvalidBoardAccessAttemptException;
import com.wherehouse.board.exception.InvalidBoardFoundAttemptException;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * BoardViewControllerExceptionHandler
 * 
 * 게시판 MVC 컨트롤러 전용 전역 예외 처리 핸들러 (View 반환 기반)
 * 
 * - 적용 대상: com.wherehouse.board.controller 하위의 @Controller 클래스
 * - 목적: 게시글/댓글 요청 중 예외 발생 시 일관된 응답 처리
 * - 특징:
 *   • @RestControllerAdvice가 아닌 @ControllerAdvice 기반
 *   • 비정상 요청에 대해 View 반환 또는 리다이렉션 수행
 */
@ControllerAdvice(basePackages = "com.wherehouse.board.controller")
public class BoardViewControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BoardViewControllerExceptionHandler.class);

    /**
     * [404 Not Found] – 존재하지 않는 게시글 접근
     * 
     * - 발생 조건:
     *   • 게시글 상세 조회, 삭제 요청 시 boardId에 해당하는 게시글이 DB에 존재하지 않는 경우
     * - 처리 방식:
     *   • JSON 본문으로 메시지 반환
     */
    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<?> handleBoardNotFoundException(BoardNotFoundException ex) {
        logger.warn("BoardNotFoundException 발생: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * [403 Forbidden] – 게시글 접근 권한 없음
     * 
     * - 발생 조건:
     *   • /authz/board/{id} 인증 요청, 게시글 수정/삭제 요청 시 작성자 불일치
     * - 처리 방식:
     *   • JSON 본문으로 메시지 반환
     */
    @ExceptionHandler(BoardAuthorizationException.class)
    public ResponseEntity<?> BoardAuthorizationException(BoardAuthorizationException ex) {
        logger.warn("BoardAuthorizationException 발생: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * [403 Redirect] – 수정 권한이 없는 사용자에 의한 비인가 접근
     * 
     * - 발생 조건:
     *   • 게시글은 존재하지만, 로그인 사용자가 작성자가 아닐 경우
     * - 처리 방식:
     *   • 302 상태 코드 + Location 헤더 기반 클라이언트 리다이렉트
     *   • 쿼리 파라미터에 메시지 및 예외 타입 전달
     */
    @ExceptionHandler(InvalidBoardAccessAttemptException.class)
    public ResponseEntity<?> handleInvalidBoardAccessAttemptException(
									InvalidBoardAccessAttemptException ex,
									HttpServletRequest request) {
        logger.warn("InvalidBoardAccessAttemptException 발생: {}", ex.getMessage());
        return buildInvalidExceptionResponse(HttpStatus.FORBIDDEN.value(), request);
    }

    /**
     * [404 Redirect] – 존재하지 않는 게시글에 대해 수정 요청한 경우
     * 
     * - 발생 조건:
     *   • 게시글 수정 요청 시 해당 boardId가 DB에 존재하지 않음
     * - 처리 방식:
     *   • 302 상태 코드 + 리다이렉트 + 쿼리 파라미터로 예외 메시지 전달
     */
    @ExceptionHandler(InvalidBoardFoundAttemptException.class)
    public ResponseEntity<?> handleInvalidBoardFoundAttemptException(
						    		InvalidBoardAccessAttemptException ex,
									HttpServletRequest request) {
        logger.warn("InvalidBoardFoundAttemptException 발생: {}", ex.getMessage());
        return buildInvalidExceptionResponse(HttpStatus.FORBIDDEN.value(), request);
    }

    /**
     * [공통 JSON 본문 반환] – 일반적인 예외에 대한 응답 JSON 구성
     * 
     * @param status HTTP 상태 코드
     * @param message 예외 메시지
     * @return JSON 형식 응답 (message 필드 포함)
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("message", message);
        return new ResponseEntity<>(errorBody, status);
    }

    /**
     * [302 Redirect] – 정책적으로 차단된 비정상 접근에 대한 리다이렉션 처리
     * 
     * - 쿼리 파라미터를 통해 에러 타입, 메시지 식별자 포함
     * - JSON 본문 없이 Location 헤더만 사용 (브라우저 자동 이동)
     * 
     * @param status 리다이렉트 유도 원인 상태 (403 or 404 등)
     * @param exceptionPage 클라이언트에서 분기 처리용 식별자
     * @param message 사용자에게 표시할 메시지
     * @return 302 응답 + 리다이렉션 URI
     */
    private ResponseEntity<Void> buildInvalidExceptionResponse(int statusCode, HttpServletRequest request) {
    	
    	String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String context = request.getContextPath(); 

        // 인코딩된 쿼리 파라미터를 포함한 리다이렉트 URI 구성
        URI redirectLocation = URI.create(String.format("%s://%s:%d%s/exception?stateCode=%d", scheme, host, port, context, statusCode));	// 404

        return ResponseEntity.status(HttpStatus.FOUND)
                             .location(redirectLocation)
                             .build();
    }
}
