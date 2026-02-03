package com.wherehouse.information.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 안전성 점수 DTO
 * 파출소 거리, CCTV 개수, 검거율을 기반으로 계산된 안전성 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyScoreDto {

    @JsonProperty("total")
    private Integer total;  // 종합 안전성 점수 (0-100)

    @JsonProperty("police_distance")
    private Integer policeDistance;  // 가장 가까운 파출소까지 거리 (미터)

    @JsonProperty("cctv_count")
    private Integer cctvCount;  // 반경 내 CCTV 총 개수

    @JsonProperty("cctv_list")
    private List<CctvDetailDto> cctvList;  // 각 CCTV의 상세 정보 (주소, 좌표, 카메라 대수)

    @JsonProperty("arrest_rate")
    private Double arrestRate;  // 해당 지역 검거율 (0.0 ~ 1.0)

    @JsonProperty("nearest_police_office")
    private PoliceOfficeDto nearestPoliceOffice;  // 새로 추가
}