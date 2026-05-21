package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7203 — 예약 없음. */
public class VisitReservationNotFoundException extends VisitReservationException {
    public VisitReservationNotFoundException(String message) {
        super("E7203", HttpStatus.NOT_FOUND, message);
    }
}
