package com.wherehouse.recommand.exception.Class;

import com.wherehouse.recommand.exception.Dto.ErrorCode;

/**
 * 데이터를 찾을 수 없는 경우 예외
 */
public class DataNotFoundException extends BusinessException {

    public DataNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DataNotFoundException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}