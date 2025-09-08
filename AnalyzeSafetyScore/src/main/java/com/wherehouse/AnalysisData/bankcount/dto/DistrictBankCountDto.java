package com.wherehouse.AnalysisData.bankcount.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictBankCountDto {

    private String districtName;
    private Long totalBankCount; // SUM 결과는 Long 타입이 더 안전합니다.
}