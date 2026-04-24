package com.wherehouse.PropertyManagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * F002 월세 매물 수정 요청 DTO.
 *
 * 엔드포인트: PATCH /api/v1/properties/monthly/{propertyId}
 *
 * PATCH 시맨틱 — 모든 필드가 선택. 요청 본문에 포함된 필드만 갱신 대상.
 *
 * 월세 전용 가변 속성 선언:
 *   deposit     — 보증금(만원)
 *   monthlyRent — 월세금(만원)
 *   buildYear   — 건축연도
 *   dealDate    — 계약일자
 *
 * CharterUpdateRequestDto 와의 차이: monthlyRent 필드가 존재한다.
 * 월세 수정이므로 monthlyRent 는 선택적 수정 대상이다(@NotNull 아님, PATCH 시맨틱).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyUpdateRequestDto {

    /**
     * 보증금(만원). 선택.
     */
    @Positive(message = "보증금은 양수여야 합니다")
    private Integer deposit;

    /**
     * 월세금(만원). 선택.
     */
    @Positive(message = "월세금은 양수여야 합니다")
    private Integer monthlyRent;

    /**
     * 건축연도. 선택. 1900 이상.
     */
    @Min(value = 1900, message = "건축연도는 1900 이상이어야 합니다")
    private Integer buildYear;

    /**
     * 계약일자. 선택. ISO 8601 날짜(YYYY-MM-DD).
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "계약일자는 YYYY-MM-DD 형식이어야 합니다")
    private String dealDate;
}