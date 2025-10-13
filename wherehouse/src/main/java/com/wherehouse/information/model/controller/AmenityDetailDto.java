package com.wherehouse.information.model.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 편의시설 카테고리별 상세 정보 DTO
 * 예: 편의점(CS2), 카페(CE7), 지하철역(SW8) 등
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmenityDetailDto {

    @JsonProperty("category_code")
    private String categoryCode;  // 카테고리 코드 (예: "CS2")

    @JsonProperty("category_name")
    private String categoryName;  // 카테고리 이름 (예: "편의점")

    @JsonProperty("count")
    private Integer count;  // 해당 카테고리의 총 장소 개수

    @JsonProperty("closest_distance")
    private Integer closestDistance;  // 가장 가까운 장소까지 거리 (미터)

    @JsonProperty("places")
    private List<PlaceDto> places;  // 해당 카테고리에 속한 모든 장소 목록
}