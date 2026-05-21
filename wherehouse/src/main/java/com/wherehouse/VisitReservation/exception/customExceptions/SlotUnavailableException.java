package com.wherehouse.VisitReservation.exception.customExceptions;

import com.wherehouse.VisitReservation.dto.SlotQueryResponseDto;
import com.wherehouse.VisitReservation.entity.LeaseType;
import com.wherehouse.VisitReservation.exception.VisitReservationException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;

/**
 * E7007 / E7008 / E7013 — 슬롯이 사용 불가 (이미 예약됨 / 상태 부적합 / 시작 시각 경과).
 *
 * 설계 명세서 섹션 7.4, 9.2 가 본 거부 코드에 한해 같은 매물·같은 임대 유형의 현재
 * 예약 가능한 슬롯 목록 (available_slots) 과 그 임대 유형 (lease_type) 을 거부 응답에
 * 함께 포함하도록 규정한다. 본 예외는 그 부가 정보를 함께 운반하며, 글로벌 핸들러가
 * 응답 본문에 추가한다.
 */
public class SlotUnavailableException extends VisitReservationException {

    private final LeaseType leaseType;
    private final List<SlotQueryResponseDto.SlotItem> availableSlots;

    private SlotUnavailableException(String errorCode, String message,
                                     LeaseType leaseType,
                                     List<SlotQueryResponseDto.SlotItem> availableSlots) {
        super(errorCode, HttpStatus.CONFLICT, message);
        this.leaseType = leaseType;
        this.availableSlots = availableSlots == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(availableSlots);
    }

    /** E7007 — 슬롯이 이미 다른 탐색자에게 확정됨. */
    public static SlotUnavailableException alreadyReserved(
            LeaseType leaseType, List<SlotQueryResponseDto.SlotItem> availableSlots) {
        return new SlotUnavailableException(
                "E7007", "해당 슬롯은 이미 예약되었습니다.", leaseType, availableSlots);
    }

    /** E7008 — 슬롯 상태가 예약 가능이 아님 (CLOSED/WITHDRAWN 등). */
    public static SlotUnavailableException notAvailableStatus(
            LeaseType leaseType, List<SlotQueryResponseDto.SlotItem> availableSlots) {
        return new SlotUnavailableException(
                "E7008", "해당 슬롯은 예약 가능 상태가 아닙니다.", leaseType, availableSlots);
    }

    /** E7013 — 슬롯 시작 시각이 이미 지남 (종료 컴포넌트 사이클 사이의 경계 상태). */
    public static SlotUnavailableException startTimeExpired(
            LeaseType leaseType, List<SlotQueryResponseDto.SlotItem> availableSlots) {
        return new SlotUnavailableException(
                "E7013", "슬롯 시작 시각이 이미 지났습니다.", leaseType, availableSlots);
    }

    public LeaseType getLeaseType() {
        return leaseType;
    }

    public List<SlotQueryResponseDto.SlotItem> getAvailableSlots() {
        return availableSlots;
    }
}
