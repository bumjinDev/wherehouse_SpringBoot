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
 * F003 방문 슬롯 조회 응답 DTO (설계 명세서 섹션 7.3).
 *
 * 임대 유형별로 분리된 응답 구조. lease_type 쿼리 매개변수를 지정하면 해당 유형의
 * 필드만, 생략하면 동일 매물 식별자에 존재하는 두 유형의 필드를 모두 포함한다.
 *
 * 슬롯에는 AVAILABLE 또는 RESERVED 만 포함되며, CLOSED/WITHDRAWN 은 제외된다 (섹션 6.3).
 * 예약자 신원은 포함되지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlotQueryResponseDto {

    @JsonProperty("property_id")
    private String propertyId;

    /** 전세 슬롯 목록. 해당 유형이 조회 대상이 아니거나 슬롯이 없으면 null. */
    @JsonProperty("charter")
    private List<SlotItem> charter;

    /** 월세 슬롯 목록. 해당 유형이 조회 대상이 아니거나 슬롯이 없으면 null. */
    @JsonProperty("monthly")
    private List<SlotItem> monthly;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SlotItem {

        @JsonProperty("slot_id")
        private Long slotId;

        @JsonProperty("start_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startTime;

        @JsonProperty("end_time")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endTime;

        @JsonProperty("status")
        private String status;
    }
}
