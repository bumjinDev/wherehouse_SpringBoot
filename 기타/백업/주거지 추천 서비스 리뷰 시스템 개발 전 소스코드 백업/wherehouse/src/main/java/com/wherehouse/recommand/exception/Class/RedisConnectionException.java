package com.wherehouse.recommand.exception.Class;

import com.wherehouse.recommand.exception.Dto.ErrorCode;

public class RedisConnectionException extends BusinessException {

    public RedisConnectionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RedisConnectionException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public RedisConnectionException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }
}