package com.wherehouse.AnalysisData.residentcenter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 주민센터 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictResidentCenterCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 주민센터 수
    private Long coordinateCount;       // 좌표 정보 보유 주민센터 수
    private Double density;             // 주민센터 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictResidentCenterCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 좌표 보유율 계산 (좌표 정보가 있는 주민센터 비율)
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
     * 주민센터 수 등급 반환 (상/중/하)
     * @return 주민센터 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 25) {
            return "상";
        } else if (totalCount >= 15) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 인구 대비 주민센터 밀도 등급 (주민센터가 많을수록 행정 접근성이 좋음)
     * @return 행정 접근성 등급
     */
    public String getAccessibilityGrade() {
        if (totalCount == null || totalCount == 0) {
            return "낮음";
        }
        if (totalCount >= 30) {
            return "매우높음";
        } else if (totalCount >= 20) {
            return "높음";
        } else if (totalCount >= 10) {
            return "보통";
        } else {
            return "낮음";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 주민센터 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: 총 %d개", districtName, totalCount != null ? totalCount : 0));

        if (coordinateCount != null) {
            sb.append(String.format(", 좌표보유 %d개", coordinateCount));
        }

        sb.append(String.format(", 접근성 %s", getAccessibilityGrade()));

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictResidentCenterCountDto{district='%s', total=%d, coordinate=%d, grade='%s', accessibility='%s'}",
                districtName, totalCount, coordinateCount, getCountGrade(), getAccessibilityGrade());
    }
}

/**
 * 구별 읍면동별 주민센터 분포를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DistrictEupmeondongDto {

    private String districtName;        // 자치구명
    private String eupmeondong;         // 읍면동명
    private Long count;                 // 해당 읍면동의 주민센터 수
    private Double percentage;          // 해당 구 내에서의 비율

    /**
     * 기본 생성자
     */
    public DistrictEupmeondongDto(String districtName, String eupmeondong, Long count) {
        this.districtName = districtName;
        this.eupmeondong = eupmeondong;
        this.count = count;
    }

    /**
     * 읍면동 유형 분류
     */
    public String getEupmeondongType() {
        if (eupmeondong == null) {
            return "분류불가";
        }

        String type = eupmeondong.trim();
        if (type.endsWith("읍")) {
            return "읍";
        } else if (type.endsWith("면")) {
            return "면";
        } else if (type.endsWith("동")) {
            return "동";
        } else {
            return "기타";
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s: %d개 (%.1f%%) - %s",
                districtName, eupmeondong, count,
                percentage != null ? percentage * 100 : 0.0,
                getEupmeondongType());
    }
}