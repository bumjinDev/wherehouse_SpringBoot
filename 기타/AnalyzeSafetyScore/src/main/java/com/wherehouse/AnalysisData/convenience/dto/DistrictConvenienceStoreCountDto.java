package com.wherehouse.AnalysisData.convenience.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 편의점 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictConvenienceStoreCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 편의점 수
    private Long activeCount;           // 영업중인 편의점 수
    private Long inactiveCount;         // 영업중이 아닌 편의점 수
    private Long coordinateCount;       // 좌표 정보 보유 편의점 수
    private Double density;             // 편의점 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictConvenienceStoreCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 활성화율 계산 (영업중인 편의점 비율)
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
     * 좌표 보유율 계산 (좌표 정보가 있는 편의점 비율)
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
     * 편의점 수 등급 반환 (상/중/하)
     * @return 편의점 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 500) {
            return "상";
        } else if (totalCount >= 200) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 편의점 현황 요약
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
        return String.format("DistrictConvenienceStoreCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade());
    }
}

/**
 * 구별 영업상태별 편의점 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DistrictConvenienceStoreStatusDto {

    private String districtName;        // 자치구명
    private String statusName;          // 영업상태명
    private Long count;                 // 해당 상태의 편의점 수
    private Double percentage;          // 해당 구 내에서의 비율

    /**
     * 기본 생성자
     */
    public DistrictConvenienceStoreStatusDto(String districtName, String statusName, Long count) {
        this.districtName = districtName;
        this.statusName = statusName;
        this.count = count;
    }

    /**
     * 상태별 설명 반환
     */
    public String getStatusDescription() {
        if (statusName == null) {
            return "상태불명";
        }
        switch (statusName) {
            case "영업":
                return "정상 영업중";
            case "폐업":
                return "폐업";
            case "휴업":
                return "일시 휴업";
            default:
                return statusName;
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s: %d개 (%.1f%%)",
                districtName, statusName, count, percentage != null ? percentage * 100 : 0.0);
    }
}