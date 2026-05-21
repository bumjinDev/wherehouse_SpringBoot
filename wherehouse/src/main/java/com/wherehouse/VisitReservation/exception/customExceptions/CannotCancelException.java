package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7009 — 취소 불가 (슬롯 시작 시간이 이미 경과). */
public class CannotCancelException extends VisitReservationException {
    public CannotCancelException(String message) {
        super("E7009", HttpStatus.CONFLICT, message);
    }
}
