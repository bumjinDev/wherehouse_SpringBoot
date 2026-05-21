package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7201 — 윈도우 없음. */
public class VisitWindowNotFoundException extends VisitReservationException {
    public VisitWindowNotFoundException(String message) {
        super("E7201", HttpStatus.NOT_FOUND, message);
    }
}
