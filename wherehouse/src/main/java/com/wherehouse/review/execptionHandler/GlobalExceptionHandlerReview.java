package com.wherehouse.review.execptionHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 리뷰 도메인 통합 예외 핸들러
 *
 * review 패키지 내에서 발생하는 모든 비즈니스 예외를 처리한다.
 * 각 커스텀 예외 타입에 따라 적절한 HTTP 상태 코드와 메시지를 반환한다.
 *
 * 예외 → HTTP 상태 코드 매핑:
 *   ReviewDuplicateException    → 409 Conflict
 *   ReviewXssException          → 400 Bad Request
 *   ReviewNotFoundException     → 404 Not Found
 *   ReviewAccessDeniedException → 403 Forbidden
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wherehouse.review")
public class GlobalExceptionHandlerReview {

    /**
     * 중복 리뷰 작성 (409 Conflict)
     */
    @ExceptionHandler(ReviewDuplicateException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(ReviewDuplicateException e) {
        log.warn("[REVIEW_DUPLICATE] {}", e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * XSS HTML 태그 감지 (400 Bad Request)
     */
    @ExceptionHandler(ReviewXssException.class)
    public ResponseEntity<Map<String, String>> handleXss(ReviewXssException e) {
        log.warn("[REVIEW_XSS] {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 리뷰 미존재 (404 Not Found)
     */
    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ReviewNotFoundException e) {
        log.warn("[REVIEW_NOT_FOUND] {}", e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /**
     * 본인 리뷰가 아닌 리소스 접근 (403 Forbidden)
     */
    @ExceptionHandler(ReviewAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(ReviewAccessDeniedException e) {
        log.warn("[REVIEW_ACCESS_DENIED] {}", e.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    /**
     * 공통 응답 생성
     */
    private ResponseEntity<Map<String, String>> buildResponse(HttpStatus status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}