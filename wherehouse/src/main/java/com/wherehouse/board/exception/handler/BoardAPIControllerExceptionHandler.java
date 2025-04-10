package com.wherehouse.board.exception.handler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.wherehouse.board.exception.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 게시판 API 예외 처리 핸들러
 * 
 * - 적용 대상: com.wherehouse.board.controller 패키지 내부 컨트롤러
 * - 모든 예외는 JSON 형식의 HTTP 응답으로 반환
 * - 클라이언트 측 JS는 응답 코드 및 메시지를 기반으로 UI 제어 가능
 * - 403/404/400 등 상황별 커스텀 예외를 명확히 분리 처리
 */
@RestControllerAdvice(basePackages = "com.wherehouse.board.controller")
public class BoardAPIControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BoardAPIControllerExceptionHandler.class);

    /**
     * 게시글 일반 인가 실패 (403)
     *
     * @param ex BoardAuthorizationException – 게시글 작성자가 아닌 사용자의 접근
     * @return JSON 응답 – { "message": 예외 메시지 }
     */
    @ExceptionHandler(BoardAuthorizationException.class)
    public ResponseEntity<?> handleBoardAuthorizationException(BoardAuthorizationException ex) {
        logger.warn("BoardAuthorizationException 발생: {}", ex.getMessage());
        return buildExceptionResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * 게시글 존재하지 않음 (404)
     *
     * @param ex BoardNotFoundException – 존재하지 않는 게시글 ID 요청
     * @return JSON 응답 – { "message": 예외 메시지 }
     */
    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<?> handleBoardNotFoundException(BoardNotFoundException ex) {
        logger.warn("BoardNotFoundException 발생: {}", ex.getMessage());
        return buildExceptionResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 게시글 수정 비인가 접근 시도 (403)
     *
     * @param ex InvalidBoardAccessAttemptException – 작성자가 아닌 사용자가 수정 요청
     * @return 302 리다이렉트 응답 (Location 헤더 포함)
     */
    @ExceptionHandler(InvalidBoardAccessAttemptException.class)
    public ResponseEntity<?> handleInvalidBoardAccessAttemptException(
    								InvalidBoardAccessAttemptException ex,
    								HttpServletRequest request) {
        logger.warn("InvalidBoardAccessAttemptException 발생: {}", ex.getMessage());
        return buildInvalidExceptionResponse(HttpStatus.FORBIDDEN.value(), request);
    }

    /**
     * 수정 대상 게시글 없음 (404)
     *
     * @param ex InvalidBoardFoundAttemptException – 비인가 접근으로 인한 잘못된 게시글 ID로 수정 요청
     * @return 302 리다이렉트 응답 (Location 헤더 포함)
     */
    @ExceptionHandler(InvalidBoardFoundAttemptException.class)
    public ResponseEntity<?> handleInvalidBoardFoundAttemptException(
    								InvalidBoardFoundAttemptException ex,
    								HttpServletRequest request) {
        logger.warn("InvalidBoardFoundAttemptException 발생: {}", ex.getMessage());
        return buildInvalidExceptionResponse(HttpStatus.NOT_FOUND.value(), request);
    }

    /**
     * DTO 유효성 검증 실패 (400)
     *
     * @param ex MethodArgumentNotValidException – @Valid 검증 실패 시 발생
     * @return JSON 응답 – { "field": "에러 메시지", ... }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        
    	Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        logger.warn("MethodArgumentNotValidException 발생: {}", errors);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(errors);
    }

    /**
     * 통상적인 예외에 대한 JSON 응답 생성기
     *
     * @param status HTTP 상태 코드 (403, 404 등)
     * @param message 예외 메시지
     * @return JSON 응답 – { "message": 메시지 }
     */
    private ResponseEntity<Map<String, Object>> buildExceptionResponse(HttpStatus status, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("message", message);

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody);
    }

    /**
     * 의도적인 잘못된 요청에 대한 리다이렉트 응답 생성기 (302 Found)
     *
     * @param status HTTP 상태 코드 (일반적으로 HttpStatus.FOUND)
     * @param exceptionPage 예외 발생 위치 식별자 (쿼리 스트링으로 포함)
     * @param message 예외 메시지 (리다이렉트 시 함께 전달)
     * @return ResponseEntity – Location 헤더 포함한 302 리다이렉트 응답
     */
    private ResponseEntity<Void> buildInvalidExceptionResponse(int statusCode, HttpServletRequest request) {
    	
    	String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String context = request.getContextPath(); 

        // 인코딩된 쿼리 파라미터를 포함한 리다이렉트 URI 구성
        URI redirectLocation = URI.create(String.format("%s://%s:%d%s/boards/exception?stateCode=%d", scheme, host, port, context, statusCode));	// 404

        return ResponseEntity.status(HttpStatus.FOUND)
                             .location(redirectLocation)
                             .build();
    }
}
