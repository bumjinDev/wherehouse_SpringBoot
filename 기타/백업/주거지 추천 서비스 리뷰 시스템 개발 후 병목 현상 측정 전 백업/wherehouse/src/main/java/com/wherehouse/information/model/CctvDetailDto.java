package com.wherehouse.information.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CCTV 상세 정보 DTO
 * 카카오맵에 마커 표시를 위한 개별 CCTV 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CctvDetailDto {

    @JsonProperty("address")
    private String address;  // CCTV 설치 주소

    @JsonProperty("latitude")
    private Double latitude;  // CCTV 위치 위도

    @JsonProperty("longitude")
    private Double longitude;  // CCTV 위치 경도

    @JsonProperty("camera_count")
    private Integer cameraCount;  // 해당 위치의 카메라 대수
}