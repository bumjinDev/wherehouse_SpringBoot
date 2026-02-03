package com.wherehouse.information.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 개별 장소 정보 DTO
 * 카카오맵 API에서 조회한 각 편의시설의 상세 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceDto {

    @JsonProperty("name")
    private String name;  // 장소 이름 (예: "GS25 서소문점")

    @JsonProperty("latitude")
    private Double latitude;  // 장소 위도

    @JsonProperty("longitude")
    private Double longitude;  // 장소 경도

    @JsonProperty("distance")
    private Integer distance;  // 요청 좌표로부터의 거리 (미터)
}