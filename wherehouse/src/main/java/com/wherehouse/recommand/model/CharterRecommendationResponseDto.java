package com.wherehouse.recommand.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharterRecommendationResponseDto {

    @JsonProperty("searchStatus")
    private String searchStatus;

    @JsonProperty("message")
    private String message;

    @JsonProperty("recommendedDistricts")
    private List<RecommendedCharterDistrictDto> recommendedDistricts;
}