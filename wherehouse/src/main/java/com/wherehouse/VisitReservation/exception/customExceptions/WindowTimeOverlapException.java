package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/** E7011 — 윈도우 시간 겹침 (동일 매물·임대 유형의 활성 윈도우와 시간이 겹침). */
public class WindowTimeOverlapException extends VisitReservationException {
    public WindowTimeOverlapException(String message) {
        super("E7011", HttpStatus.CONFLICT, message);
    }
}
