package com.wherehouse.AnalysisData.police.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 경찰시설 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictPoliceFacilityCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 경찰시설 수
    private Long policeStationCount;    // 파출소 수
    private Long securityBoxCount;      // 지구대 수
    private Long coordinateCount;       // 좌표 정보 보유 시설 수
    private String facilityType;        // 시설유형 (세부 분류시 사용)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictPoliceFacilityCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 좌표 보유율 계산 (좌표 정보가 있는 시설 비율)
     * @return 좌표 보유율 (0.0 ~ 1.0)
     */
    public Double getCoordinateRate() {
        if (totalCount == null || totalCount == 0) {
            return 0.0;
        }
        if (coordinateCount == null) {
            return null;
        }
        return coordinateCount.doubleValue() / totalCount.doubleValue();
    }

    /**
     * 경찰시설 수 등급 반환 (상/중/하)
     * @return 경찰시설 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 30) {
            return "상";
        } else if (totalCount >= 15) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 치안안전도 등급 반환 (경찰시설 수 기준)
     * @return 치안안전도 등급
     */
    public String getSecuritySafetyGrade() {
        if (totalCount == null) {
            return "알수없음";
        }
        if (totalCount >= 40) {
            return "매우안전";
        } else if (totalCount >= 30) {
            return "안전";
        } else if (totalCount >= 15) {
            return "보통";
        } else {
            return "개선필요";
        }
    }

    /**
     * 시설 밀도 분석
     * @return 시설 밀도 설명
     */
    public String getDensityAnalysis() {
        if (totalCount == null) {
            return "분석불가";
        }
        if (totalCount >= 50) {
            return "매우조밀";
        } else if (totalCount >= 30) {
            return "조밀";
        } else if (totalCount >= 15) {
            return "적정";
        } else {
            return "부족";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 경찰시설 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: 총 %,d개", districtName, totalCount != null ? totalCount : 0));

        if (policeStationCount != null) {
            sb.append(String.format(", 파출소 %,d개", policeStationCount));
        }

        if (coordinateCount != null) {
            sb.append(String.format(", 좌표보유 %,d개", coordinateCount));
        }

        sb.append(String.format(" (%s)", getSecuritySafetyGrade()));

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictPoliceFacilityCountDto{district='%s', total=%d, station=%d, coordinate=%d, grade='%s', safety='%s'}",
                districtName, totalCount, policeStationCount, coordinateCount, getCountGrade(), getSecuritySafetyGrade());
    }
}