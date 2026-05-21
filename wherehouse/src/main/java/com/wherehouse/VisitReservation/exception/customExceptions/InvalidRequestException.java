package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

/**
 * E7001 — 입력값 유효성 검증 실패 (Bean Validation 외 서비스·컨트롤러 계층 수동 검증).
 *
 * MethodArgumentNotValidException 가 잡지 못하는 검증 (쿼리 매개변수 enum 변환, 윈도우
 * 길이가 슬롯 분할 단위의 정수배여야 한다는 비즈니스 검증 등) 에서 사용한다.
 */
public class InvalidRequestException extends VisitReservationException {
    public InvalidRequestException(String message) {
        super("E7001", HttpStatus.BAD_REQUEST, message);
    }
}
