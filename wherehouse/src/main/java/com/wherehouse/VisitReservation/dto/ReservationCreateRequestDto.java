package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * F004 슬롯 예약 요청 DTO (설계 명세서 섹션 7.4).
 *
 * 탐색자 식별자는 @AuthenticationPrincipal 로 주입되므로 본 DTO 에는 없다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationCreateRequestDto {

    @JsonProperty("slot_id")
    @NotNull(message = "슬롯 식별자는 필수입니다")
    @Positive(message = "슬롯 식별자는 양수여야 합니다")
    private Long slotId;
}
