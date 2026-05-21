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
 * F001 윈도우 공개 응답 DTO (설계 명세서 섹션 7.1).
 *
 * 생성된 윈도우 정보와 그에 분할된 슬롯 목록을 함께 반환한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WindowCreateResponseDto {

    @JsonProperty("window_id")
    private Long windowId;

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

    @JsonProperty("slot_duration_minutes")
    private Integer slotDurationMinutes;

    /** 분할 생성된 슬롯 목록 (시작 시각 순). */
    @JsonProperty("slots")
    private List<SlotItem> slots;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 응답에 포함되는 슬롯 항목.
     */
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
