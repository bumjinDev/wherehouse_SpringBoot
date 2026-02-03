package com.wherehouse.AnalysisData.school.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 학교 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictSchoolCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 학교 수
    private Long activeCount;           // 운영중인 학교 수
    private Long inactiveCount;         // 운영중이 아닌 학교 수
    private Long coordinateCount;       // 좌표 정보 보유 학교 수
    private Double density;             // 학교 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictSchoolCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 운영율 계산 (운영중인 학교 비율)
     * @return 운영율 (0.0 ~ 1.0)
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
     * 좌표 보유율 계산 (좌표 정보가 있는 학교 비율)
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
     * 학교 수 등급 반환 (상/중/하)
     * @return 학교 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 100) {
            return "상";
        } else if (totalCount >= 50) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 학교 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: 총 %d개", districtName, totalCount != null ? totalCount : 0));

        if (activeCount != null) {
            sb.append(String.format(", 운영중 %d개", activeCount));
        }

        if (coordinateCount != null) {
            sb.append(String.format(", 좌표보유 %d개", coordinateCount));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictSchoolCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade());
    }
}

/**
 * 구별 학교급별 학교 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DistrictSchoolLevelDto {

    private String districtName;        // 자치구명
    private String schoolLevel;         // 학교급 (초등학교, 중학교, 고등학교 등)
    private Long count;                 // 해당 학교급의 학교 수
    private Double percentage;          // 해당 구 내에서의 비율

    /**
     * 기본 생성자
     */
    public DistrictSchoolLevelDto(String districtName, String schoolLevel, Long count) {
        this.districtName = districtName;
        this.schoolLevel = schoolLevel;
        this.count = count;
    }

    /**
     * 학교급별 설명 반환
     */
    public String getSchoolLevelDescription() {
        if (schoolLevel == null) {
            return "학교급불명";
        }
        switch (schoolLevel) {
            case "초등학교":
                return "초등교육기관";
            case "중학교":
                return "중등교육기관(전기)";
            case "고등학교":
                return "중등교육기관(후기)";
            case "특수학교":
                return "특수교육기관";
            default:
                return schoolLevel;
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s: %d개 (%.1f%%)",
                districtName, schoolLevel, count, percentage != null ? percentage * 100 : 0.0);
    }
}