package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7206 — 알림 없음. */
public class VisitNotificationNotFoundException extends VisitReservationException {
    public VisitNotificationNotFoundException(String message) {
        super("E7206", HttpStatus.NOT_FOUND, message);
    }
}
