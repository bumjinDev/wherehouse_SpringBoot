package com.wherehouse.AnalysisData.pcbang.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 PC방 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictPcBangCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 PC방 수
    private Long activeCount;           // 정상 영업중인 PC방 수
    private Long inactiveCount;         // 폐업/정지 등인 PC방 수
    private Long coordinateCount;       // 좌표 정보 보유 PC방 수

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictPcBangCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 활성화율 계산 (정상 영업중인 PC방 비율)
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
     * 좌표 보유율 계산 (좌표 정보가 있는 PC방 비율)
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
     * PC방 수 등급 반환 (상/중/하)
     * @return PC방 수 등급
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
     * @return 구별 PC방 현황 요약
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
        return String.format("DistrictPcBangCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade());
    }
}