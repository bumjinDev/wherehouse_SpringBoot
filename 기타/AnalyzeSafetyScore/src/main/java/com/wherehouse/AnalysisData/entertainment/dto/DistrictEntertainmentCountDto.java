package com.wherehouse.AnalysisData.entertainment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 구별 유흥업소 수 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistrictEntertainmentCountDto {

    private String districtName;        // 자치구명
    private Long totalCount;            // 총 유흥업소 수
    private Long activeCount;           // 영업중인 유흥업소 수
    private Long inactiveCount;         // 영업중이 아닌 유흥업소 수
    private Long coordinateCount;       // 좌표 정보 보유 유흥업소 수
    private Double density;             // 유흥업소 밀도 (선택적 - 인구나 면적 대비)

    /**
     * 기본 생성자 (구명과 총 개수만)
     */
    public DistrictEntertainmentCountDto(String districtName, Long totalCount) {
        this.districtName = districtName;
        this.totalCount = totalCount;
    }

    /**
     * 영업율 계산 (영업중인 유흥업소 비율)
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
     * 좌표 보유율 계산 (좌표 정보가 있는 유흥업소 비율)
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
     * 유흥업소 수 등급 반환 (상/중/하)
     * @return 유흥업소 수 등급
     */
    public String getCountGrade() {
        if (totalCount == null) {
            return "데이터없음";
        }
        if (totalCount >= 50) {
            return "상";
        } else if (totalCount >= 25) {
            return "중";
        } else {
            return "하";
        }
    }

    /**
     * 유흥업소 밀도 등급 (야간 상권 활성도)
     * @return 야간 상권 등급
     */
    public String getNightLifeGrade() {
        if (totalCount == null || totalCount == 0) {
            return "낮음";
        }
        if (totalCount >= 80) {
            return "매우높음";
        } else if (totalCount >= 50) {
            return "높음";
        } else if (totalCount >= 25) {
            return "보통";
        } else {
            return "낮음";
        }
    }

    /**
     * 요약 정보 문자열 반환
     * @return 구별 유흥업소 현황 요약
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

        sb.append(String.format(", 야간상권 %s", getNightLifeGrade()));

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("DistrictEntertainmentCountDto{district='%s', total=%d, active=%d, coordinate=%d, grade='%s', nightlife='%s'}",
                districtName, totalCount, activeCount, coordinateCount, getCountGrade(), getNightLifeGrade());
    }
}

/**
 * 구별 유흥업소 카테고리별 집계 결과를 담는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DistrictEntertainmentCategoryDto {

    private String districtName;        // 자치구명
    private String businessCategory;    // 업종 카테고리 (유흥주점, 단란주점 등)
    private Long count;                 // 해당 카테고리의 유흥업소 수
    private Double percentage;          // 해당 구 내에서의 비율

    /**
     * 기본 생성자
     */
    public DistrictEntertainmentCategoryDto(String districtName, String businessCategory, Long count) {
        this.districtName = districtName;
        this.businessCategory = businessCategory;
        this.count = count;
    }

    /**
     * 유흥업소 카테고리별 설명 반환
     */
    public String getCategoryDescription() {
        if (businessCategory == null) {
            return "카테고리불명";
        }

        String category = businessCategory.trim();
        if (category.contains("유흥주점")) {
            return "유흥주점 (룸살롱, 단란주점 등)";
        } else if (category.contains("단란주점")) {
            return "단란주점 (노래방 겸업)";
        } else if (category.contains("노래연습장")) {
            return "노래연습장 (코인노래방 등)";
        } else if (category.contains("당구장")) {
            return "당구장 (포켓볼장 포함)";
        } else if (category.contains("게임")) {
            return "게임시설 (오락실 등)";
        } else {
            return businessCategory;
        }
    }

    /**
     * 유흥업소 규모 분류
     */
    public String getEntertainmentScale() {
        if (businessCategory == null) {
            return "분류불가";
        }

        String category = businessCategory.toLowerCase();
        if (category.contains("대형") || category.contains("클럽")) {
            return "대형";
        } else if (category.contains("일반")) {
            return "일반";
        } else if (category.contains("소형") || category.contains("노래방")) {
            return "소형";
        } else {
            return "기타";
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s: %d개 (%.1f%%) - %s",
                districtName, businessCategory, count,
                percentage != null ? percentage * 100 : 0.0,
                getEntertainmentScale());
    }
}