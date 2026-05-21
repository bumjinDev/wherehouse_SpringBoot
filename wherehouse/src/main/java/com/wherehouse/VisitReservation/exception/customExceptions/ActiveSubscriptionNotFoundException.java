package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7205 — 활성 구독 없음 (해제 시도 시 본인의 활성 구독이 없음). */
public class ActiveSubscriptionNotFoundException extends VisitReservationException {
    public ActiveSubscriptionNotFoundException(String message) {
        super("E7205", HttpStatus.NOT_FOUND, message);
    }
}
