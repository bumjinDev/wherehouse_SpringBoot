package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F005 예약 취소 응답 DTO (설계 명세서 섹션 7.5).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationCancelResponseDto {

    @JsonProperty("reservation_id")
    private Long reservationId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("cancelled_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime cancelledAt;

    @JsonProperty("reopened_slot")
    private ReopenedSlot reopenedSlot;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReopenedSlot {

        @JsonProperty("slot_id")
        private Long slotId;

        @JsonProperty("status")
        private String status;
    }
}
