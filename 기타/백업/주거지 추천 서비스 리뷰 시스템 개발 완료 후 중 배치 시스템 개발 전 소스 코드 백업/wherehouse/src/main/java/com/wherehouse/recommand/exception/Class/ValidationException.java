package com.wherehouse.recommand.exception.Class;

import com.wherehouse.recommand.exception.Dto.ErrorCode;

/**
 * 파라미터 검증 실패 예외
 */
public class ValidationException extends BusinessException {

    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ValidationException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}