package com.wherehouse.AnalysisData.streetlight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 병원 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictHospitalCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 병원 수
    private Long activeCount;           // 영업중인 병원 수
    private Long inactiveCount;         // 폐업한 병원 수
    private Long coordinateCount;       // 좌표 정보 보유 병원 수
    private String businessTypeName;    // 업종명 (세부 분류시 사용)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictHospitalCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 활성화율 계산 (영업중인 병원 비율)
     * @return 활성화율 (0.0 ~ 1.0)
     */
    public Double getActiveRate() {
        if (totalCount == null || totalCount == 0) {
            return 0.0;
        }
        if (activeCount == null) {
            return null;
        }
        return activeCount.doubleValue() / totalCount.doubleValue();
    }

    /**
     * 좌표 보유율 계산 (좌표 정보가 있는 병원 비율)
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
     * 병원 수 등급 반환 (상/중/하)
     * @return 병원 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 200) {
            return "상";
        } else if (totalCount >= 100) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 의료접근성 등급 반환 (병원 수 기준)
     * @return 의료접근성 등급
     */
    public String getMedicalAccessibilityGrade() {
        if (totalCount == null) {
            return "알수없음";
        }
        if (totalCount >= 300) {
            return "매우우수";
        } else if (totalCount >= 200) {
            return "우수";
        } else if (totalCount >= 100) {
            return "보통";
        } else {
            return "개선필요";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 병원 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: 총 %,d개", districtName, totalCount != null ? totalCount : 0));

        if (activeCount != null) {
            sb.append(String.format(", 영업중 %,d개", activeCount));
        }

        if (coordinateCount != null) {
            sb.append(String.format(", 좌표보유 %,d개", coordinateCount));
        }

        sb.append(String.format(" (%s)", getMedicalAccessibilityGrade()));

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictHospitalCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s', accessibility='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade(), getMedicalAccessibilityGrade());
    }
}