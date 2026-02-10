package com.wherehouse.members.exception.handler;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wherehouse.members.exception.JwtKeyNotFoundException;
import com.wherehouse.members.exception.MemberNotFoundException;
import com.wherehouse.members.exception.NicknameAlreadyExistsException;
import com.wherehouse.members.exception.ReservedNicknameException;
import com.wherehouse.members.exception.ReservedUserIdException;
import com.wherehouse.members.exception.UserIdAlreadyExistsException;


@Order(1)
@RestControllerAdvice(basePackages = "com.wherehouse.members.controller")
public class MemberAPIControllerExceptionHandler {		/* 클래스 'BindException' 상속 받음.  */

    private static final Logger logger = LoggerFactory.getLogger(MemberAPIControllerExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {

        logger.info("MemberControllerExceptionHandler.handleValidationExceptions()!");

        Map<String, String> errors = new HashMap<>();

        /**
         * @Valid 유효성 검증 실패 시 처리 로직
         *
         * - MethodArgumentNotValidException에서 getBindingResult()로 BindingResult 추출
         * - getFieldErrors()를 통해 유효성 실패 필드 목록(List<FieldError>) 조회
         *   • getField() → 실패한 필드명
         *   • getDefaultMessage() → 지정된 에러 메시지(@NotBlank 등)
         *
         * - 필드명과 메시지를 Map<String, String>에 저장하여 클라이언트에 JSON 응답
         * - JS 단에서는 각 필드 메시지를 기반으로 오류 표시 처리 (join.js, modify.js)
         *
         * - 유의사항:
         *   • @Valid 어노테이션은 어노테이션 종류와 관계없이 MethodArgumentNotValidException 발생
         *   • 오류 메시지는 FieldError.getDefaultMessage()를 통해 분기 가능
         */
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /* ===== Serivce 빈에서 발생한 예외 처리 ==== */

    /**
     * 422 Unprocessable Entity – 예약어 ID 사용 시도
     *
     * "admin", "root", "system" 등 정책상 사용 불가능한 ID로 가입 시도 시 발생.
     * 409(DB 중복)와 구분되는 정책적 거부이므로 422를 반환한다.
     */
    @ExceptionHandler(ReservedUserIdException.class)
    public ResponseEntity<?> handleReservedUserId(ReservedUserIdException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /**
     * 422 Unprocessable Entity – 예약어 닉네임 사용 시도
     *
     * "admin", "root", "system" 등 정책상 사용 불가능한 닉네임으로 가입/수정 시도 시 발생.
     * 409(DB 중복)와 구분되는 정책적 거부이므로 422를 반환한다.
     */
    @ExceptionHandler(ReservedNicknameException.class)
    public ResponseEntity<?> handleReservedNickname(ReservedNicknameException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /**
     * 409 Conflict – 아이디 중복
     *
     * Service 계층에서 동일 ID 존재 시 UserIdAlreadyExistsException을 throw하면
     * 이 핸들러가 409 + JSON 에러 응답을 반환한다.
     * 클라이언트(join.js)에서는 response.status === 409 분기에서 메시지를 표시한다.
     */
    @ExceptionHandler(UserIdAlreadyExistsException.class)
    public ResponseEntity<?> handleUserIdDuplicate(UserIdAlreadyExistsException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 409 Conflict – 닉네임 중복
     */
    @ExceptionHandler(NicknameAlreadyExistsException.class)
    public ResponseEntity<?> handleNicknameDuplicate(NicknameAlreadyExistsException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 404 Not Found – 회원을 찾을 수 없음
     */
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<?> handleMemberNotFound(MemberNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * 401 Unauthorized – JWT 키 없음
     */
    @ExceptionHandler(JwtKeyNotFoundException.class)
    public ResponseEntity<?> handleJwtKeyNotFound(JwtKeyNotFoundException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * 공통 응답 포맷 생성, 서버는 이 응답인 에러 코드 및 메시지만 제공하고 실제 리다이렉트 등은 브라우저에서 이걸 받아서 처리한다.
     */
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("code", status.value());
        errorData.put("status", status.getReasonPhrase());
        errorData.put("message", message);
        return new ResponseEntity<>(errorData, status);
    }
}