package com.wherehouse.AnalysisData.banklocation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BankLocationCountDto {

    private String districtName;
    private Long totalCount;
}