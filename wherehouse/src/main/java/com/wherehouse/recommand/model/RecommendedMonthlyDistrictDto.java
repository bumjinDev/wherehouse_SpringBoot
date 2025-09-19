package com.wherehouse.recommand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedMonthlyDistrictDto {
    private Integer rank;
    private String districtName;
    private String summary;
    private List<TopMonthlyPropertyDto> topProperties;
}