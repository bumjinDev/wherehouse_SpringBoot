package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7002 — 유효하지 않은 상태 전이 (이미 철회/취소/종료된 자원에 대한 동작). */
public class InvalidStateTransitionException extends VisitReservationException {
    public InvalidStateTransitionException(String message) {
        super("E7002", HttpStatus.CONFLICT, message);
    }
}
