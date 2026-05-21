package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F008 탐색자 예약 현황 응답 DTO (설계 명세서 섹션 7.9).
 *
 * 탐색자 본인의 모든 예약 (확정·취소·무효화·종료) 을 최신 확정 순으로 반환한다.
 * 등록자 연락 정보는 예약 상태가 CONFIRMED 또는 COMPLETED 일 때만 포함된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearcherReservationListDto {

    @JsonProperty("reservations")
    private List<ReservationItem> reservations;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReservationItem {

        @JsonProperty("reservation_id")
        private Long reservationId;

        @JsonProperty("slot_id")
        private Long slotId;

        @JsonProperty("property_id")
        private String propertyId;

        @JsonProperty("lease_type")
        private String leaseType;

        @JsonProperty("start_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startTime;

        @JsonProperty("end_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endTime;

        @JsonProperty("status")
        private String status;

        @JsonProperty("visit_result")
        private String visitResult;

        @JsonProperty("confirmed_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime confirmedAt;

        /** 등록자 연락 정보. CONFIRMED 또는 COMPLETED 일 때만 채워진다. */
        @JsonProperty("registrant")
        private ContactInfoDto registrant;
    }
}
