package com.wherehouse.AnalysisData.cctv.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictCctvCountDto {

    private String districtName;
    private Long totalCount;
}