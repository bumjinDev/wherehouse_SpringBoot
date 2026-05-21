package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7004 — 자기 매물 예약 불가 (등록자가 자기 매물의 슬롯을 예약하려 함). */
public class SelfPropertyReservationException extends VisitReservationException {
    public SelfPropertyReservationException(String message) {
        super("E7004", HttpStatus.FORBIDDEN, message);
    }
}
