package com.wherehouse.VisitReservation.exception;

import com.wherehouse.VisitReservation.exception.customExceptions.SlotUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
 * 방문 예약 도메인 통합 예외 핸들러 (설계 명세서 섹션 9).
 *
 * 응답 포맷 (섹션 9.2):
 *   {
 *     "error_code": "E7xxx",
 *     "message":   "...",
 *     "timestamp": "2026-06-10T15:30:01"
 *   }
 *
 * 특수 응답 (섹션 7.4, 9.2):
 *   E7007 / E7008 / E7013 거부 시에는 본문에 lease_type 과 available_slots 가 추가된다.
 *   {@link SlotUnavailableException} 이 운반하는 부가 정보를 그대로 응답에 포함한다.
 *
 * 처리 범위 (basePackages):
 *   본 핸들러는 com.wherehouse.VisitReservation 의 컨트롤러에서 발생한 예외만 처리한다.
 *   기존 매물 도메인(com.wherehouse.PropertyManagement) 의 핸들러와는 패키지 분리로
 *   상호 간섭이 없다.
 *
 * 401 인증 실패는 JWT 필터 계층 (ApiAuthenticationEntryPoint) 이 처리하므로 본 핸들러
 * 대상이 아니다.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.wherehouse.VisitReservation")
public class GlobalExceptionHandlerVisit {

    // ================================================================
    // 방문 예약 비즈니스 예외 (E7xxx) — 베이스 단일 진입점
    // ================================================================

    /**
     * 가장 구체적인 분기. E7007/E7008/E7013 의 거부 응답에 부가되는 lease_type 과
     * available_slots 를 본문에 추가한다.
     */
    @ExceptionHandler(SlotUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleSlotUnavailable(SlotUnavailableException e) {
        log.warn("[VISIT_SLOT_UNAVAILABLE] code={}, message={}", e.getErrorCode(), e.getMessage());

        Map<String, Object> body = baseBody(e.getErrorCode(), e.getMessage());
        if (e.getLeaseType() != null) {
            body.put("lease_type", e.getLeaseType().name());
        }
        body.put("available_slots", e.getAvailableSlots());

        return ResponseEntity.status(e.getHttpStatus()).body(body);
    }

    /**
     * 베이스 클래스 fallback. 각 구체 예외가 자신의 errorCode 와 httpStatus 를 보유하므로
     * 핸들러는 그 두 값을 읽어 응답을 구성한다.
     */
    @ExceptionHandler(VisitReservationException.class)
    public ResponseEntity<Map<String, Object>> handleVisitReservation(VisitReservationException e) {
        log.warn("[VISIT_{}] code={}, message={}",
                e.getHttpStatus().is4xxClientError() ? "CLIENT_ERROR" : "SERVER_ERROR",
                e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .body(baseBody(e.getErrorCode(), e.getMessage()));
    }

    // ================================================================
    // 프레임워크 레벨 예외
    // ================================================================

    /** Bean Validation 실패 — E7001. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("요청 파라미터가 유효하지 않습니다.");
        log.warn("[VISIT_VALIDATION_FAILED] {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(baseBody("E7001", message));
    }

    /** JSON 파싱 실패 — E7001. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("[VISIT_MESSAGE_INVALID] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(baseBody("E7001", "요청 본문 형식이 올바르지 않습니다."));
    }

    /**
     * 데이터 무결성 위반 — 부분 유일 제약 (UQ_VISIT_RESERVATION_CONFIRMED_SLOT,
     * UQ_REOPEN_SUBSCRIPTION_ACTIVE, UQ_VISIT_SLOT_WINDOW_START) 이 데이터베이스 차원에서
     * 백스톱으로 동작했음을 의미한다. 본 분기는 상위 비즈니스 검증을 우회한 마지막 사고에
     * 한해 발동하며, 의미 분류가 어려우므로 409 로 통일하여 반환한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("[VISIT_INTEGRITY_BACKSTOP] {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(baseBody("E7002", "동시 처리로 인한 무결성 충돌이 발생했습니다."));
    }

    // ================================================================
    // 공통 응답 생성
    // ================================================================

    private Map<String, Object> baseBody(String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error_code", errorCode);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return body;
    }
}
