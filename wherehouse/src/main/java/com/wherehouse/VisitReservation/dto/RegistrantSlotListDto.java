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
 * F008 등록자 슬롯 현황 응답 DTO (설계 명세서 섹션 7.11).
 *
 * 임대 유형별로 분리된 응답 구조 (charter/monthly). lease_type 매개변수 처리 정책은
 * 탐색자용 슬롯 조회 (섹션 7.3) 와 일관성을 유지한다.
 *
 * 각 슬롯에는 연결된 예약 정보와 (예약 상태가 CONFIRMED·COMPLETED 일 때) 탐색자
 * 연락 정보를 함께 반환한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistrantSlotListDto {

    @JsonProperty("property_id")
    private String propertyId;

    @JsonProperty("charter")
    private List<SlotItem> charter;

    @JsonProperty("monthly")
    private List<SlotItem> monthly;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SlotItem {

        @JsonProperty("slot_id")
        private Long slotId;

        @JsonProperty("window_id")
        private Long windowId;

        @JsonProperty("start_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startTime;

        @JsonProperty("end_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endTime;

        @JsonProperty("status")
        private String status;

        /** 연결된 예약. 슬롯에 확정·취소·무효화·종료 예약이 있을 때 채워지고, 없으면 null. */
        @JsonProperty("reservation")
        private ReservationInfo reservation;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReservationInfo {

        @JsonProperty("reservation_id")
        private Long reservationId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("visit_result")
        private String visitResult;

        /** 탐색자 연락 정보. 예약 상태가 CONFIRMED 또는 COMPLETED 일 때만 채워진다. */
        @JsonProperty("searcher")
        private ContactInfoDto searcher;
    }
}
