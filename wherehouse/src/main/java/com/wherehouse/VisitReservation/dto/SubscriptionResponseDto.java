package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F006 재개방 알림 구독 신청·해제 응답 공통 DTO (설계 명세서 섹션 7.6, 7.7).
 *
 * 신청 응답에는 subscription_id 와 subscribed_at, 해제 응답에는 terminated_at 이
 * 채워진다. 양쪽 모두에서 status 와 slot_id 는 공통.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionResponseDto {

    @JsonProperty("subscription_id")
    private Long subscriptionId;

    @JsonProperty("slot_id")
    private Long slotId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("subscribed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime subscribedAt;

    @JsonProperty("terminated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime terminatedAt;
}
