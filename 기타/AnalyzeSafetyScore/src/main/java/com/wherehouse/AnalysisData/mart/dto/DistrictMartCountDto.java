package com.wherehouse.AnalysisData.mart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 마트 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictMartCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 마트 수
    private Long activeCount;           // 영업중인 마트 수
    private Long inactiveCount;         // 영업중이 아닌 마트 수
    private Long coordinateCount;       // 좌표 정보 보유 마트 수
    private Double density;             // 마트 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictMartCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 영업율 계산 (영업중인 마트 비율)
     * @return 영업율 (0.0 ~ 1.0)
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
     * 좌표 보유율 계산 (좌표 정보가 있는 마트 비율)
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
     * 마트 수 등급 반환 (상/중/하)
     * @return 마트 수 등급
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
     * 요약 정보 문자열 반환
     * @return 구별 마트 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: 총 %d개", districtName, totalCount != null ? totalCount : 0));

        if (activeCount != null) {
            sb.append(String.format(", 영업중 %d개", activeCount));
        }

        if (coordinateCount != null) {
            sb.append(String.format(", 좌표보유 %d개", coordinateCount));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictMartCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade());
    }
}

/**
 * 구별 마트 업종별 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DistrictMartTypeDto {

    private String districtName;        // 자치구명
    private String businessTypeName;    // 업종명 (백화점, 대형마트 등)
    private Long count;                 // 해당 업종의 마트 수
    private Double percentage;          // 해당 구 내에서의 비율

    /**
     * 기본 생성자
     */
    public DistrictMartTypeDto(String districtName, String businessTypeName, Long count) {
        this.districtName = districtName;
        this.businessTypeName = businessTypeName;
        this.count = count;
    }

    /**
     * 업종별 설명 반환
     */
    public String getBusinessTypeDescription() {
        if (businessTypeName == null) {
            return "업종불명";
        }
        switch (businessTypeName) {
            case "백화점":
                return "대형 종합 쇼핑시설";
            case "대형마트":
                return "대규모 할인점";
            case "슈퍼마켓":
                return "중소형 식료품점";
            case "마트":
                return "일반 마트";
            default:
                return businessTypeName;
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s: %d개 (%.1f%%)",
                districtName, businessTypeName, count, percentage != null ? percentage * 100 : 0.0);
    }
}