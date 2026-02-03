package com.wherehouse.recommand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

// 기존 코드를 이것으로 바꾸세요
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedDistrictDto {
    private Integer rank;
    private String districtName;
    private String summary;
    private List<TopPropertyDto> topProperties;
}