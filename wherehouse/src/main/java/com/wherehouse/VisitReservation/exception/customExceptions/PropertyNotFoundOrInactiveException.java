package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7204 — 매물 없음 또는 비활성 (존재하지 않거나 STATUS 가 ACTIVE 가 아님). */
public class PropertyNotFoundOrInactiveException extends VisitReservationException {
    public PropertyNotFoundOrInactiveException(String message) {
        super("E7204", HttpStatus.NOT_FOUND, message);
    }
}
