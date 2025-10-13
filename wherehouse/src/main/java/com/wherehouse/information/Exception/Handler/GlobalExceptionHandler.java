package com.wherehouse.information.Exception.Handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리 핸들러
 * 모든 컨트롤러에서 발생하는 예외를 중앙에서 처리
 */
@RestControllerAdvice  // 모든 @RestController에 적용
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Bean Validation 실패 시 처리
     * @Valid 검증 실패 시 자동 호출
     * 예: latitude가 -90 미만일 때
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("errorCode", "INVALID_PARAMETER");

        // 첫 번째 검증 오류 메시지 추출
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        errorResponse.put("message", message);

        log.error("Validation error: {}", message);

        // HTTP 400 Bad Request 반환
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 모든 예상치 못한 예외 처리
     * 위에서 처리되지 않은 모든 예외를 잡아서 500 에러 반환
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception ex) {

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("errorCode", "SERVER_ERROR");
        errorResponse.put("message", "요청 처리 중 내부 서버 오류가 발생했습니다.");

        // 전체 스택 트레이스 로깅 (디버깅용)
        log.error("Internal server error: ", ex);

        // HTTP 500 Internal Server Error 반환
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}