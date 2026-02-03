package com.wherehouse.recommand.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 유흥업소 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(야간 상권 활성도) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_ENTERTAINMENT_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisEntertainmentStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_entertainment_seq")
    @SequenceGenerator(name = "analysis_entertainment_seq",
            sequenceName = "SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BUSINESS_STATUS_NAME", length = 4000)
    private String businessStatusName;          // 영업상태명

    @Column(name = "PHONE_NUMBER", length = 4000)
    private String phoneNumber;                 // 전화번호

    @Column(name = "JIBUN_ADDRESS", length = 4000)
    private String jibunAddress;                // 지번주소

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;                 // 도로명주소

    @Column(name = "BUSINESS_NAME", length = 4000)
    private String businessName;                // 사업장명

    @Column(name = "BUSINESS_CATEGORY", length = 4000)
    private String businessCategory;            // 업종 카테고리

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 4000)
    private String hygieneBusinessType;         // 위생업종

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                // 위도

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;               // 경도

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisEntertainmentStatistics fromSourceData(
            String businessStatusName,
            String phoneNumber,
            String jibunAddress,
            String roadAddress,
            String businessName,
            String businessCategory,
            String hygieneBusinessType) {

        return AnalysisEntertainmentStatistics.builder()
                .businessStatusName(nullToDefault(businessStatusName, "데이터없음"))
                .phoneNumber(nullToDefault(phoneNumber, "데이터없음"))
                .jibunAddress(nullToDefault(jibunAddress, "데이터없음"))
                .roadAddress(nullToDefault(roadAddress, "데이터없음"))
                .businessName(nullToDefault(businessName, "데이터없음"))
                .businessCategory(nullToDefault(businessCategory, "데이터없음"))
                .hygieneBusinessType(nullToDefault(hygieneBusinessType, "데이터없음"))
                .build();
    }

    /**
     * 좌표 정보 설정
     */
    public void setCoordinates(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * 좌표 정보 유효성 검증 (한국 영역 내 좌표 여부)
     */
    public boolean isValidKoreanCoordinates() {
        if (latitude == null || longitude == null) {
            return false;
        }

        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();

        // 한국 영역: 위도 33.0~38.7, 경도 124.0~132.0
        return lat >= 33.0 && lat <= 38.7 && lng >= 124.0 && lng <= 132.0;
    }

    /**
     * 주소 정보 반환 (도로명주소 우선, 없으면 지번주소)
     */
    public String getPrimaryAddress() {
        if (roadAddress != null && !roadAddress.trim().isEmpty() && !"데이터없음".equals(roadAddress)) {
            return roadAddress;
        }
        if (jibunAddress != null && !jibunAddress.trim().isEmpty() && !"데이터없음".equals(jibunAddress)) {
            return jibunAddress;
        }
        return null;
    }

    /**
     * 영업 중인 유흥업소인지 확인
     */
    public boolean isActive() {
        return businessStatusName != null && "영업".equals(businessStatusName.trim());
    }

    /**
     * 폐업한 유흥업소인지 확인
     */
    public boolean isClosed() {
        return businessStatusName != null &&
                (businessStatusName.trim().contains("폐업") ||
                        businessStatusName.trim().contains("휴업"));
    }

    /**
     * NULL 값을 기본값으로 변환하는 유틸리티 메서드
     */
    private static String nullToDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * 좌표 정보 보유 여부
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * 유효한 주소 정보 보유 여부
     */
    public boolean hasValidAddress() {
        return getPrimaryAddress() != null;
    }

    /**
     * 유흥업소 카테고리별 분류 (유흥주점/단란주점/노래방/당구장/기타)
     */
    public String getEntertainmentCategory() {
        if (businessCategory == null) {
            return "기타";
        }

        String category = businessCategory.trim().toLowerCase();
        if (category.contains("유흥주점") || category.contains("룸살롱")) {
            return "유흥주점";
        } else if (category.contains("단란주점")) {
            return "단란주점";
        } else if (category.contains("노래연습장") || category.contains("노래방")) {
            return "노래연습장";
        } else if (category.contains("당구장") || category.contains("포켓볼")) {
            return "당구장";
        } else if (category.contains("게임") || category.contains("오락")) {
            return "게임시설";
        } else {
            return "기타";
        }
    }

    /**
     * 고급 유흥업소 여부 (대형 클럽, 고급 룸살롱 등)
     */
    public boolean isHighEndEntertainment() {
        if (businessCategory == null && businessName == null) {
            return false;
        }

        String category = (businessCategory != null ? businessCategory : "").toLowerCase();
        String name = (businessName != null ? businessName : "").toLowerCase();

        return category.contains("클럽") ||
                category.contains("대형") ||
                name.contains("클럽") ||
                (category.contains("유흥주점") && name.contains("호텔"));
    }

    /**
     * 소규모 유흥업소 여부 (노래방, 소형 당구장 등)
     */
    public boolean isSmallScaleEntertainment() {
        String category = getEntertainmentCategory();
        return "노래연습장".equals(category) ||
                "당구장".equals(category) ||
                "게임시설".equals(category);
    }

    /**
     * 위생업종 기준 분류
     */
    public String getHygieneCategory() {
        if (hygieneBusinessType == null) {
            return "미분류";
        }

        String type = hygieneBusinessType.trim();
        if (type.contains("유흥")) {
            return "유흥업";
        } else if (type.contains("단란")) {
            return "단란주점업";
        } else if (type.contains("노래")) {
            return "노래연습장업";
        } else {
            return type;
        }
    }

    /**
     * 야간 상권 기여도 점수 (1-10)
     */
    public int getNightlifeContributionScore() {
        String category = getEntertainmentCategory();

        switch (category) {
            case "유흥주점":
                return isHighEndEntertainment() ? 10 : 8;
            case "단란주점":
                return 7;
            case "노래연습장":
                return 5;
            case "당구장":
                return 4;
            case "게임시설":
                return 3;
            default:
                return 2;
        }
    }

    @Override
    public String toString() {
        return String.format("AnalysisEntertainmentStatistics{id=%d, businessName='%s', category='%s', status='%s', hasCoordinates=%s}",
                id, businessName, businessCategory, businessStatusName, hasCoordinates());
    }
}