package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F007 방문 결과 분류 응답 DTO (설계 명세서 섹션 7.8).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultClassifyResponseDto {

    @JsonProperty("reservation_id")
    private Long reservationId;

    @JsonProperty("visit_result")
    private String visitResult;

    @JsonProperty("result_classified_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime resultClassifiedAt;
}
