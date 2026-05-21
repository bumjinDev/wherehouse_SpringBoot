package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7014 — 자기 매물 슬롯에 대한 구독 불가 (자기가 등록한 매물의 슬롯). */
public class SelfPropertySubscriptionException extends VisitReservationException {
    public SelfPropertySubscriptionException(String message) {
        super("E7014", HttpStatus.FORBIDDEN, message);
    }
}
