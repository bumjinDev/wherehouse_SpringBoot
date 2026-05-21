package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * F007 방문 결과 분류 요청 DTO (설계 명세서 섹션 7.8).
 *
 * 등록자가 종료된 예약에 대해 VISITED 또는 NO_SHOW 를 분류한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultClassifyRequestDto {

    @JsonProperty("visit_result")
    @NotBlank(message = "방문 결과는 필수입니다")
    @Pattern(regexp = "^(VISITED|NO_SHOW)$", message = "방문 결과는 VISITED 또는 NO_SHOW 입니다")
    private String visitResult;
}
