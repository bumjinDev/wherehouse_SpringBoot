package com.wherehouse.information.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 편의성 점수 DTO
 * 주변 편의시설 정보를 기반으로 계산된 편의성 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvenienceScoreDto {

    @JsonProperty("total")
    private Integer total;  // 종합 편의성 점수 (0-100)

    @JsonProperty("amenity_details")
    private List<AmenityDetailDto> amenityDetails;  // 카테고리별 편의시설 상세 정보
}