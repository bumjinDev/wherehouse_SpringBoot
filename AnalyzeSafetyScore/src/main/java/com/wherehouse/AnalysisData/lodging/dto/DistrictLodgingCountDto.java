package com.wherehouse.AnalysisData.lodging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 숙박업 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictLodgingCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 숙박업 수
    private Long activeCount;           // 영업중인 숙박업 수
    private Long inactiveCount;         // 영업중이 아닌 숙박업 수
    private Long coordinateCount;       // 좌표 정보 보유 숙박업 수
    private Double density;             // 숙박업 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictLodgingCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 영업율 계산 (영업중인 숙박업 비율)
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
     * 좌표 보유율 계산 (좌표 정보가 있는 숙박업 비율)
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
     * 숙박업 수 등급 반환 (상/중/하)
     * @return 숙박업 수 등급
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
     * 숙박업 밀도 등급 (관광 인프라 수준)
     * @return 관광 인프라 등급
     */
    public String getTourismInfraGrade() {
        if (totalCount == null || totalCount == 0) {
            return "낮음";
        }
        if (totalCount >= 150) {
            return "매우높음";
        } else if (totalCount >= 100) {
            return "높음";
        } else if (totalCount >= 50) {
            return "보통";
        } else {
            return "낮음";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 숙박업 현황 요약
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

        sb.append(String.format(", 관광인프라 %s", getTourismInfraGrade()));

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictLodgingCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s', tourism='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade(), getTourismInfraGrade());
    }
}

/**
 * 구별 숙박업 유형별 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DistrictLodgingTypeDto {

    private String districtName;        // 자치구명
    private String businessTypeName;    // 업종명 (여관업, 관광호텔업 등)
    private Long count;                 // 해당 업종의 숙박업 수
    private Double percentage;          // 해당 구 내에서의 비율

    /**
     * 기본 생성자
     */
    public DistrictLodgingTypeDto(String districtName, String businessTypeName, Long count) {
        this.districtName = districtName;
        this.businessTypeName = businessTypeName;
        this.count = count;
    }

    /**
     * 숙박업 유형별 설명 반환
     */
    public String getBusinessTypeDescription() {
        if (businessTypeName == null) {
            return "업종불명";
        }

        String type = businessTypeName.trim();
        if (type.contains("호텔")) {
            return "호텔업 (고급 숙박시설)";
        } else if (type.contains("여관")) {
            return "여관업 (일반 숙박시설)";
        } else if (type.contains("모텔")) {
            return "모텔업 (단기 숙박시설)";
        } else if (type.contains("펜션")) {
            return "펜션업 (휴양 숙박시설)";
        } else if (type.contains("게스트하우스")) {
            return "게스트하우스 (공유 숙박시설)";
        } else {
            return businessTypeName;
        }
    }

    /**
     * 숙박업 등급 분류
     */
    public String getLodgingGrade() {
        if (businessTypeName == null) {
            return "분류불가";
        }

        String type = businessTypeName.toLowerCase();
        if (type.contains("특급") || type.contains("5성") || type.contains("럭셔리")) {
            return "특급";
        } else if (type.contains("1급") || type.contains("고급") || type.contains("호텔")) {
            return "고급";
        } else if (type.contains("2급") || type.contains("일반")) {
            return "일반";
        } else {
            return "기타";
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s: %d개 (%.1f%%) - %s",
                districtName, businessTypeName, count,
                percentage != null ? percentage * 100 : 0.0,
                getLodgingGrade());
    }
}