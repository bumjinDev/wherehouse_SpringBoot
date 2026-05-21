package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7003 — 권한 없음 (본인의 매물/예약/구독/알림이 아님). */
public class UnauthorizedAccessException extends VisitReservationException {
    public UnauthorizedAccessException(String message) {
        super("E7003", HttpStatus.FORBIDDEN, message);
    }
}
