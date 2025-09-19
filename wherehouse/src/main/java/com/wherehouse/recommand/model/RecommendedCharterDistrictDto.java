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
public class RecommendedCharterDistrictDto {

    private Integer rank;
    private String districtName;
    private String summary;
    private List<TopCharterPropertyDto> topProperties;

    // === 2차 명세: 상세 순위 정보 패널용 점수 데이터 추가 ===
    @JsonProperty("averagePriceScore")
    private Double averagePriceScore;

    @JsonProperty("averageSpaceScore")
    private Double averageSpaceScore;

    @JsonProperty("districtSafetyScore")
    private Double districtSafetyScore;
}