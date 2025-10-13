package com.wherehouse.information.model.controller;

// ============================================
// 3. Response DTO (최상위)
// ============================================

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
public class LocationAnalysisResponseDTO {

    @JsonProperty("analysis_status")
    private String analysisStatus;

    @JsonProperty("coordinate")
    private CoordinateDto coordinate;

    @JsonProperty("address")
    private AddressDto address;

    @JsonProperty("safety_score")
    private SafetyScoreDto safetyScore;

    @JsonProperty("convenience_score")
    private ConvenienceScoreDto convenienceScore;

    @JsonProperty("overall_score")
    private Integer overallScore;

    @JsonProperty("recommendations")
    private List<String> recommendations;

    @JsonProperty("warnings")
    private List<String> warnings;
}