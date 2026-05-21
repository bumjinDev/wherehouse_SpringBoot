package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7202 — 슬롯 없음. */
public class VisitSlotNotFoundException extends VisitReservationException {
    public VisitSlotNotFoundException(String message) {
        super("E7202", HttpStatus.NOT_FOUND, message);
    }
}
