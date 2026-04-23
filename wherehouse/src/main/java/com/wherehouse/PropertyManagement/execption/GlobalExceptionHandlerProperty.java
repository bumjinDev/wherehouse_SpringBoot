package com.wherehouse.PropertyManagement.execption;

import com.wherehouse.PropertyManagement.execption.customExceptions.DuplicatePropertyException;
import com.wherehouse.PropertyManagement.execption.customExceptions.InvalidStateForUpdateException;
import com.wherehouse.PropertyManagement.execption.customExceptions.InvalidStatusTransitionException;
import com.wherehouse.PropertyManagement.execption.customExceptions.PropertyNotFoundException;
import com.wherehouse.PropertyManagement.execption.customExceptions.PropertyValidationException;
import com.wherehouse.PropertyManagement.execption.customExceptions.UnauthorizedPropertyAccessException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 매물 도메인 통합 예외 핸들러
 *
 * 설계 명세서 참조: 섹션 5.2, 7.1 에러 응답 공통 포맷, 7.8 에러 코드 체계
 *
 * 응답 포맷 (섹션 7.1):
 *   {
 *     "errorCode": "E4xxx",
 *     "message":   "...",
 *     "timestamp": "2026-04-21T14:30:00"
 *   }
 *
 * 에러 코드 매핑 (섹션 7.8):
 *   E4001 (400) — 요청 파라미터 유효성 실패, 불변 속성 수정 시도, 서비스 2차 검증 실패
 *   E4002 (400) — 허용되지 않는 상태 전이
 *   E4003 (403) — 등록자 본인 아님, 배치 매물 수정 시도
 *   E4105 (409) — 동일 MD5 해시 매물 이미 존재 (F006 중복 감지)
 *   E4106 (409) — 종료 상태(COMPLETED/DELETED) 매물 수정 시도
 *   E4201 (404) — 매물 미존재 또는 DELETED 상태 접근
 *
 * 범위 외:
 *   E4101 (401 인증 실패)는 JWT 필터 계층이 처리하므로 본 핸들러 대상 아님.
 *   E4104는 기존 리뷰 도메인 고유(중복 리뷰 작성)로 매물 고도화와 무관.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wherehouse.PropertyManagement")
public class GlobalExceptionHandlerProperty {

    // ================================================================
    // 커스텀 비즈니스 예외
    // ================================================================

    @ExceptionHandler(PropertyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePropertyNotFound(PropertyNotFoundException e) {
        log.warn("[PROPERTY_NOT_FOUND] {}", e.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "E4201", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedPropertyAccessException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedAccess(UnauthorizedPropertyAccessException e) {
        log.warn("[PROPERTY_ACCESS_DENIED] {}", e.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "E4003", e.getMessage());
    }

    @ExceptionHandler(InvalidStateForUpdateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidState(InvalidStateForUpdateException e) {
        log.warn("[PROPERTY_INVALID_STATE] {}", e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "E4106", e.getMessage());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTransition(InvalidStatusTransitionException e) {
        log.warn("[PROPERTY_INVALID_TRANSITION] {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "E4002", e.getMessage());
    }

    @ExceptionHandler(DuplicatePropertyException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicatePropertyException e) {
        log.warn("[PROPERTY_DUPLICATE] {}", e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "E4105", e.getMessage());
    }

    /**
     * 서비스 계층 2차 검증 실패 (400 Bad Request, E4001).
     * Bean Validation 으로 표현 불가한 비즈니스 규칙 위반 (자치구 코드 화이트리스트 등).
     */
    @ExceptionHandler(PropertyValidationException.class)
    public ResponseEntity<Map<String, String>> handlePropertyValidation(PropertyValidationException e) {
        log.warn("[PROPERTY_VALIDATION_FAILED_SERVICE] {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "E4001", e.getMessage());
    }

    // ================================================================
    // 프레임워크 레벨 예외 (Bean Validation, JSON 파싱)
    // ================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("요청 파라미터가 유효하지 않습니다.");
        log.warn("[PROPERTY_VALIDATION_FAILED] {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, "E4001", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[PROPERTY_MESSAGE_INVALID] {}", e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "E4001", "요청 본문 형식이 올바르지 않습니다.");
    }

    // ================================================================
    // 공통 응답 생성
    // ================================================================

    private ResponseEntity<Map<String, String>> buildResponse(
            HttpStatus status, String errorCode, String message) {

        Map<String, String> body = new LinkedHashMap<>();
        body.put("errorCode", errorCode);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.status(status).body(body);
    }
}