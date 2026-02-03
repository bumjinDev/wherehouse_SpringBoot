package com.wherehouse.recommand.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyRecommendationRequestDto {

    @JsonProperty("budgetMin")
    @NotNull(message = "최소 보증금은 필수입니다")
    @Min(value = 0, message = "최소 보증금은 0 이상이어야 합니다")
    private Integer budgetMin;

    @JsonProperty("budgetMax")
    @NotNull(message = "최대 보증금은 필수입니다")
    @Min(value = 0, message = "최대 보증금은 0 이상이어야 합니다")
    private Integer budgetMax;

    @JsonProperty("monthlyRentMin")
    @NotNull(message = "최소 월세는 필수입니다")
    @Min(value = 0, message = "최소 월세는 0 이상이어야 합니다")
    private Integer monthlyRentMin;

    @JsonProperty("monthlyRentMax")
    @NotNull(message = "최대 월세는 필수입니다")
    @Min(value = 0, message = "최대 월세는 0 이상이어야 합니다")
    private Integer monthlyRentMax;

    @JsonProperty("areaMin")
    @NotNull(message = "최소 평수는 필수입니다")
    @DecimalMin(value = "0.0", message = "최소 평수는 0 이상이어야 합니다")
    private Double areaMin;

    @JsonProperty("areaMax")
    @NotNull(message = "최대 평수는 필수입니다")
    @DecimalMin(value = "0.0", message = "최대 평수는 0 이상이어야 합니다")
    private Double areaMax;

    @JsonProperty("priority1")
    @NotNull(message = "1순위 우선순위는 필수입니다")
    @Pattern(regexp = "^(PRICE|SAFETY|SPACE)$", message = "우선순위는 PRICE, SAFETY, SPACE 중 선택해야 합니다")
    private String priority1;

    @JsonProperty("priority2")
    @NotNull(message = "2순위 우선순위는 필수입니다")
    @Pattern(regexp = "^(PRICE|SAFETY|SPACE)$", message = "우선순위는 PRICE, SAFETY, SPACE 중 선택해야 합니다")
    private String priority2;

    @JsonProperty("priority3")
    @NotNull(message = "3순위 우선순위는 필수입니다")
    @Pattern(regexp = "^(PRICE|SAFETY|SPACE)$", message = "우선순위는 PRICE, SAFETY, SPACE 중 선택해야 합니다")
    private String priority3;

    // 선택적 파라미터들 (기본값 있음)
    @JsonProperty("budgetFlexibility")
    @Min(value = 0, message = "예산 유연성은 0 이상이어야 합니다")
    @Max(value = 100, message = "예산 유연성은 100 이하여야 합니다")
    private Integer budgetFlexibility = 0;

    @JsonProperty("minSafetyScore")
    @Min(value = 0, message = "최소 안전 점수는 0 이상이어야 합니다")
    @Max(value = 100, message = "최소 안전 점수는 100 이하여야 합니다")
    private Integer minSafetyScore = 0;

    @JsonProperty("absoluteMinArea")
    @DecimalMin(value = "0.0", message = "절대 최소 평수는 0 이상이어야 합니다")
    private Double absoluteMinArea = 0.0;

    // 커스텀 검증
    @AssertTrue(message = "최대 보증금이 최소 보증금보다 크거나 같아야 합니다")
    public boolean isBudgetValid() {
        if (budgetMin == null || budgetMax == null) return true;
        return budgetMax >= budgetMin;
    }

    @AssertTrue(message = "최대 월세가 최소 월세보다 크거나 같아야 합니다")
    public boolean isMonthlyRentValid() {
        if (monthlyRentMin == null || monthlyRentMax == null) return true;
        return monthlyRentMax >= monthlyRentMin;
    }

    @AssertTrue(message = "최대 평수가 최소 평수보다 크거나 같아야 합니다")
    public boolean isAreaValid() {
        if (areaMin == null || areaMax == null) return true;
        return areaMax >= areaMin;
    }

    @AssertTrue(message = "우선순위는 중복될 수 없습니다")
    public boolean isPriorityUnique() {
        if (priority1 == null || priority2 == null || priority3 == null) return true;
        return !priority1.equals(priority2) &&
                !priority1.equals(priority3) &&
                !priority2.equals(priority3);
    }
}