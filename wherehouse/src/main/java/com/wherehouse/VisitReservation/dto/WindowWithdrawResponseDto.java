package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F002 윈도우 철회 응답 DTO (설계 명세서 섹션 7.2).
 *
 * 철회된 윈도우의 식별자·상태·철회 시각과, 본 철회로 무효화된 확정 예약 목록을 반환한다.
 * 무효화된 각 탐색자에게는 별도 비동기 통지가 발송된다 (RESERVATION_INVALIDATED).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WindowWithdrawResponseDto {

    @JsonProperty("window_id")
    private Long windowId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("withdrawn_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime withdrawnAt;

    @JsonProperty("invalidated_reservations")
    private List<InvalidatedReservation> invalidatedReservations;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvalidatedReservation {

        @JsonProperty("reservation_id")
        private Long reservationId;

        @JsonProperty("slot_id")
        private Long slotId;

        @JsonProperty("searcher_user_id")
        private String searcherUserId;
    }
}
