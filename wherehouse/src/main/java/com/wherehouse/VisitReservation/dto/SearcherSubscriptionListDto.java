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
 * F008 탐색자 구독 현황 응답 DTO (설계 명세서 섹션 7.10).
 *
 * 탐색자 본인의 모든 구독 (활성·종료) 을 최신 구독 순으로 반환한다. 각 구독에는
 * 연결된 슬롯의 시간 정보와 현재 상태를 함께 포함한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearcherSubscriptionListDto {

    @JsonProperty("subscriptions")
    private List<SubscriptionItem> subscriptions;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubscriptionItem {

        @JsonProperty("subscription_id")
        private Long subscriptionId;

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

        @JsonProperty("slot_status")
        private String slotStatus;

        @JsonProperty("status")
        private String status;

        @JsonProperty("subscribed_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime subscribedAt;
    }
}
