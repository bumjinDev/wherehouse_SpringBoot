package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7010 — 분류 불가 (예약이 종료 상태 아니거나 이미 분류됨). */
public class CannotClassifyException extends VisitReservationException {
    public CannotClassifyException(String message) {
        super("E7010", HttpStatus.CONFLICT, message);
    }
}
