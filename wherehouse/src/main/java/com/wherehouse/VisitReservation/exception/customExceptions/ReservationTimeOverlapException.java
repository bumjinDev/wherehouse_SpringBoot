package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7006 — 시간 겹침 (기존 활성 예약의 방문 시간과 겹침). */
public class ReservationTimeOverlapException extends VisitReservationException {
    public ReservationTimeOverlapException(String message) {
        super("E7006", HttpStatus.CONFLICT, message);
    }
}
