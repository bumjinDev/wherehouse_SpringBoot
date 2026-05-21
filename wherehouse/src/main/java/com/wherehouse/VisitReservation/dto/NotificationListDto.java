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
 * 알림 조회 응답 DTO (설계 명세서 섹션 7.12).
 *
 * 한 이용자의 알림 목록을 미읽음 우선·최신순으로 반환하며, 페이징 커서 next_before 를
 * 함께 제공한다. 마지막 페이지에서는 next_before 가 null.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationListDto {

    @JsonProperty("notifications")
    private List<NotificationItem> notifications;

    /** 다음 페이지 조회 커서. null 이면 더 이상 알림이 없음. */
    @JsonProperty("next_before")
    private Long nextBefore;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class NotificationItem {

        @JsonProperty("notification_id")
        private Long notificationId;

        @JsonProperty("notification_type")
        private String notificationType;

        @JsonProperty("message")
        private String message;

        @JsonProperty("is_read")
        private Boolean isRead;

        @JsonProperty("created_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;

        @JsonProperty("related_property_id")
        private String relatedPropertyId;

        @JsonProperty("related_slot_id")
        private Long relatedSlotId;

        @JsonProperty("related_reservation_id")
        private Long relatedReservationId;
    }
}
