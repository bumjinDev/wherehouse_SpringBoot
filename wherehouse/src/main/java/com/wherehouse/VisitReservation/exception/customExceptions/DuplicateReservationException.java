package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7005 — 동일 매물 중복 예약 (같은 매물에 이미 활성 예약이 존재). */
public class DuplicateReservationException extends VisitReservationException {
    public DuplicateReservationException(String message) {
        super("E7005", HttpStatus.CONFLICT, message);
    }
}
