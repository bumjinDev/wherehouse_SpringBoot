package com.wherehouse.recommand.exception.Class;

import com.wherehouse.recommand.exception.Dto.ErrorCode;

/**
 * 비즈니스 로직 예외 처리용 기본 클래스
 * 모든 커스텀 예외의 부모 클래스 역할
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * ErrorCode만으로 예외 생성 (기본 메시지 사용)
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode와 커스텀 메시지로 예외 생성
     */
    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode, 커스텀 메시지, 원인 예외와 함께 예외 생성
     */
    public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(customMessage, cause);
        this.errorCode = errorCode;
    }

    /**
     * 에러 코드 반환
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}