package com.wherehouse.AnalysisData.population.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 구별 인구밀도 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictPopulationDensityDto {

    private String districtName;        // 자치구명
    private Long populationCount;       // 인구수
    private BigDecimal areaSize;        // 면적
    private BigDecimal populationDensity; // 인구밀도
    private Integer year;               // 기준연도

    /**
     * 기본 생성자 (구명과 인구밀도만)
     */
    public DistrictPopulationDensityDto(String districtName, BigDecimal populationDensity) {
        this.districtName = districtName;
        this.populationDensity = populationDensity;
    }

    /**
     * 인구밀도 등급 반환 (상/중/하)
     * @return 인구밀도 등급
     */
    public String getDensityGrade() {
        if (populationDensity == null) {
            return "데이터없음";
        }
        double density = populationDensity.doubleValue();
        if (density >= 20000) {
            return "상";
        } else if (density >= 10000) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 1㎢당 인구수로 표현
     * @return 1㎢당 인구수
     */
    public String getPopulationPerKm2() {
        if (populationDensity == null) {
            return "데이터없음";
        }
        return String.format("%.0f명/㎢", populationDensity.doubleValue());
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 인구밀도 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: ", districtName));

        if (populationCount != null) {
            sb.append(String.format("인구 %,d명, ", populationCount));
        }

        if (areaSize != null) {
            sb.append(String.format("면적 %.2f㎢, ", areaSize));
        }

        if (populationDensity != null) {
            sb.append(String.format("밀도 %s", getPopulationPerKm2()));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictPopulationDensityDto{district='%s', population=%d, density=%.2f, grade='%s'}",
                districtName, populationCount,
                populationDensity != null ? populationDensity.doubleValue() : 0.0,
                getDensityGrade());
    }
}