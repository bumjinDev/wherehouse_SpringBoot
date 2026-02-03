package com.wherehouse.information.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 좌표 정보 DTO
 * 위도와 경도를 담는 단순 객체
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordinateDto {

    @JsonProperty("latitude")
    private Double latitude;  // 위도

    @JsonProperty("longitude")
    private Double longitude;  // 경도
}