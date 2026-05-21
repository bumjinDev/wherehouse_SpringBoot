package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7015 — 자기 확정 예약 슬롯에 대한 구독 불가 (자기가 예약한 슬롯). */
public class SelfReservationSubscriptionException extends VisitReservationException {
    public SelfReservationSubscriptionException(String message) {
        super("E7015", HttpStatus.CONFLICT, message);
    }
}
