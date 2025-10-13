package com.wherehouse.information.model.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationAnalysisRequestDTO {

    @NotNull(message = "위도는 필수입니다")
    @Min(value = -90, message = "위도는 -90 이상이어야 합니다")
    @Max(value = 90, message = "위도는 90 이하여야 합니다")
    @JsonProperty("latitude")
    private Double latitude;

    @NotNull(message = "경도는 필수입니다")
    @Min(value = -180, message = "경도는 -180 이상이어야 합니다")
    @Max(value = 180, message = "경도는 180 이하여야 합니다")
    @JsonProperty("longitude")
    private Double longitude;

    @Min(value = 100, message = "반경은 최소 100m 이상이어야 합니다")
    @Max(value = 2000, message = "반경은 최대 2000m 이하여야 합니다")
    @JsonProperty("radius")
    @Builder.Default
    private Integer radius = 500;
}