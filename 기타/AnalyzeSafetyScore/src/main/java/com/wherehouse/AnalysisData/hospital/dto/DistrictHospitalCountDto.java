package com.wherehouse.AnalysisData.hospital.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictHospitalCountDto {

    private String districtName;
    private Long totalCount;
}