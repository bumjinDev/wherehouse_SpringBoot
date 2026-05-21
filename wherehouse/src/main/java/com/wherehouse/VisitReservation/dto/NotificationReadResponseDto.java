package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 읽음 처리 응답 DTO (설계 명세서 섹션 7.13).
 *
 * 호출은 멱등이며, 이미 읽음 상태인 알림에 다시 호출해도 동일 응답을 반환한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationReadResponseDto {

    @JsonProperty("notification_id")
    private Long notificationId;

    @JsonProperty("is_read")
    private Boolean isRead;
}
