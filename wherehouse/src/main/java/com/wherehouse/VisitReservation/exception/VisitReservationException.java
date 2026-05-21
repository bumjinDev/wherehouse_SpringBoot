package com.wherehouse.VisitReservation.exception;

import org.springframework.http.HttpStatus;

/**
 * 방문 예약 도메인의 비즈니스 예외 베이스 클래스 (설계 명세서 섹션 9.1).
 *
 * 본 도메인에서 발생하는 모든 비즈니스 예외는 본 클래스를 상속한다. 각 구체 예외는
 * 자신의 errorCode (E7xxx) 와 HTTP 상태를 super() 호출에서 고정한다. 글로벌 핸들러는
 * 본 베이스 타입을 catch 하고 errorCode 와 httpStatus 를 읽어 응답을 생성하므로,
 * 새 에러 코드 추가 시 핸들러에 별도 메서드를 추가할 필요가 없다.
 *
 * 본 베이스를 직접 사용하지 않고 항상 구체 클래스를 throw 한다. 구체 클래스명이 catch
 * 측에서 의미를 표현하며, 동일 HTTP 상태에서도 코드별로 분기할 수 있게 한다.
 */
public abstract class VisitReservationException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected VisitReservationException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected VisitReservationException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
