package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7012 — 이미 구독 중 (같은 슬롯에 활성 구독이 이미 존재). */
public class DuplicateSubscriptionException extends VisitReservationException {
    public DuplicateSubscriptionException(String message) {
        super("E7012", HttpStatus.CONFLICT, message);
    }
}
