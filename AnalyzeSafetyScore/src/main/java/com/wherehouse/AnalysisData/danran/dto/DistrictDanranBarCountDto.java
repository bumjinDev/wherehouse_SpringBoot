package com.wherehouse.AnalysisData.danran.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictDanranBarCountDto {

    private String districtName;
    private Long totalCount;
}