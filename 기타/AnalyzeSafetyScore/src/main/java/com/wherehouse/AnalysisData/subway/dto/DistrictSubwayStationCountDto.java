package com.wherehouse.AnalysisData.subway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 지하철역 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictSubwayStationCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 지하철역 수
    private Long coordinateCount;       // 좌표 정보 보유 지하철역 수
    private Double density;             // 지하철역 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictSubwayStationCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 좌표 보유율 계산 (좌표 정보가 있는 지하철역 비율)
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
     * 지하철역 수 등급 반환 (상/중/하)
     * @return 지하철역 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 20) {
            return "상";
        } else if (totalCount >= 10) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 대중교통 접근성 등급 (지하철역 수 기반)
     * @return 대중교통 접근성 등급
     */
    public String getPublicTransportAccessibilityGrade() {
        if (totalCount == null || totalCount == 0) {
            return "낮음";
        }
        if (totalCount >= 25) {
            return "매우높음";
        } else if (totalCount >= 15) {
            return "높음";
        } else if (totalCount >= 8) {
            return "보통";
        } else {
            return "낮음";
        }
    }

    /**
     * 교통 허브 여부 (지하철역이 많은 교통 중심지 여부)
     * @return 교통 허브 여부
     */
    public boolean isTransportationHub() {
        return totalCount != null && totalCount >= 20;
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 지하철역 현황 요약
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s: 총 %d개", districtName, totalCount != null ? totalCount : 0));

        if (coordinateCount != null) {
            sb.append(String.format(", 좌표보유 %d개", coordinateCount));
        }

        sb.append(String.format(", 교통접근성 %s", getPublicTransportAccessibilityGrade()));

        if (isTransportationHub()) {
            sb.append(", 교통허브");
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictSubwayStationCountDto{district='%s', total=%d, coordinate=%d, grade='%s', accessibility='%s', hub=%s}",
                districtName, totalCount, coordinateCount, getCountGrade(),
                getPublicTransportAccessibilityGrade(), isTransportationHub());
    }
}