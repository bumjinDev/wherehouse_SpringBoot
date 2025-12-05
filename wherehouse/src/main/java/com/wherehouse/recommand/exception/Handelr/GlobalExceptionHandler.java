package com.wherehouse.recommand.exception.Handelr;


import com.wherehouse.recommand.exception.Class.BusinessException;
import com.wherehouse.recommand.exception.Class.RedisConnectionException;
import com.wherehouse.recommand.exception.Dto.ErrorCode;
import com.wherehouse.recommand.model.ErrorResponseDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@RestControllerAdvice(basePackages = "com.wherehouse.recommand")
@Slf4j
public class GlobalExceptionHandler {

    /**
     * @RequestBody + @Valid 검증 실패 - POST 방식의 주요 케이스
     * Pattern, NotNull, Min/Max 등 모든 Bean Validation 어노테이션 검증 실패 시 발생
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {

        log.warn("MethodArgumentNotValidException 발생 - JSON RequestBody 검증 실패", ex);

        String errorMessage = ErrorCode.INVALID_PARAMETER.getDefaultMessage();
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        if (!fieldErrors.isEmpty()) {
            FieldError firstError = fieldErrors.get(0);
            String fieldName = firstError.getField();
            String message = firstError.getDefaultMessage();

            log.debug("검증 실패 필드: {}, 메시지: {}", fieldName, message);
            errorMessage = message;
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ErrorCode.INVALID_PARAMETER.getCode())
                .message(errorMessage)
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * JSON 파싱 오류 - 잘못된 JSON 형식으로 요청한 경우
     * HttpMessageNotReadableException 처리
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {

        log.warn("HttpMessageNotReadableException 발생 - JSON 파싱 오류", ex);

        String errorMessage = "JSON 형식이 올바르지 않습니다.";

        // Jackson 파싱 에러인 경우 더 구체적인 메시지 제공
        if (ex.getCause() instanceof com.fasterxml.jackson.core.JsonParseException) {
            errorMessage = "JSON 문법 오류입니다. 올바른 JSON 형식으로 요청해주세요.";
        } else if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) {
            errorMessage = "알 수 없는 필드가 포함되어 있습니다.";
        } else if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException) {
            errorMessage = "필드 값의 형식이 올바르지 않습니다.";
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ErrorCode.INVALID_PARAMETER_FORMAT.getCode())
                .message(errorMessage)
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * @PathVariable, @RequestParam 직접 검증 실패
     * ConstraintViolationException 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleConstraintViolationException(
            ConstraintViolationException ex) {

        log.warn("ConstraintViolationException 발생", ex);

        String errorMessage = ErrorCode.INVALID_PARAMETER.getDefaultMessage();
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();

        if (!violations.isEmpty()) {
            for (ConstraintViolation<?> violation : violations) {
                errorMessage = violation.getMessage();
                break; // 첫 번째만 가져오고 종료
            }
        }

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ErrorCode.INVALID_PARAMETER.getCode())
                .message(errorMessage)
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 타입 변환 실패 - PathVariable이나 RequestParam 타입 불일치
     * MethodArgumentTypeMismatchException 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {

        log.warn("MethodArgumentTypeMismatchException 발생", ex);

        String parameterName = ex.getName();
        String parameterValue = ex.getValue() != null ? ex.getValue().toString() : "null";
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "Unknown";

        String errorMessage = String.format(
                "'%s' 파라미터의 값 '%s'을(를) %s 타입으로 변환할 수 없습니다.",
                parameterName, parameterValue, expectedType);

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ErrorCode.INVALID_PARAMETER_TYPE.getCode())
                .message(errorMessage)
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException ex) {

        log.warn("BusinessException 발생 - 코드: {}, 메시지: {}", ex.getErrorCode().getCode(), ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Redis 연결 예외 처리
     */
    @ExceptionHandler(RedisConnectionException.class)
    public ResponseEntity<ErrorResponseDto> handleRedisConnectionException(RedisConnectionException ex) {

        log.error("Redis 연결 예외 발생", ex);

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    /**
     * 일반적인 IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException ex) {

        log.warn("IllegalArgumentException 발생", ex);

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ErrorCode.BUSINESS_RULE_VIOLATION.getCode())
                .message(ex.getMessage())
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 기타 모든 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(Exception ex) {

        log.error(ex.getMessage());

        ErrorResponseDto errorResponse = ErrorResponseDto.builder()
                .errorCode(ErrorCode.SERVER_ERROR.getCode())
                .message(ErrorCode.SERVER_ERROR.getDefaultMessage())
                .timestamp(getCurrentTimestamp())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * 현재 시간 문자열 반환
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}