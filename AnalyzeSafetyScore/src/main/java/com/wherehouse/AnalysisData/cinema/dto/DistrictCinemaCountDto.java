package com.wherehouse.AnalysisData.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictCinemaCountDto {

    private String districtName;
    private Long totalCount;
}