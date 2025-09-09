package com.wherehouse.AnalysisData.lodging.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 숙박업 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(관광 인프라) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_LODGING_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisLodgingStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_lodging_seq")
    @SequenceGenerator(name = "analysis_lodging_seq",
            sequenceName = "SEQ_ANALYSIS_LODGING_STATISTICS",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BUILDING_OWNERSHIP_TYPE", length = 4000)
    private String buildingOwnershipType;       // 건물소유구분

    @Column(name = "BUSINESS_NAME", length = 4000)
    private String businessName;                // 사업장명

    @Column(name = "BUSINESS_STATUS_NAME", length = 4000)
    private String businessStatusName;          // 영업상태명

    @Column(name = "BUSINESS_TYPE_NAME", length = 4000)
    private String businessTypeName;            // 업종명

    @Column(name = "DETAIL_STATUS_NAME", length = 4000)
    private String detailStatusName;            // 상세영업상태명

    @Column(name = "FULL_ADDRESS", length = 4000)
    private String fullAddress;                 // 지번주소

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 4000)
    private String hygieneBusinessType;         // 위생업종

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;                 // 도로명주소

    @Column(name = "SERVICE_NAME", length = 4000)
    private String serviceName;                 // 서비스명

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                // 위도

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;               // 경도

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisLodgingStatistics fromSourceData(
            String buildingOwnershipType,
            String businessName,
            String businessStatusName,
            String businessTypeName,
            String detailStatusName,
            String fullAddress,
            String hygieneBusinessType,
            String roadAddress,
            String serviceName) {

        return AnalysisLodgingStatistics.builder()
                .buildingOwnershipType(nullToDefault(buildingOwnershipType, "데이터없음"))
                .businessName(nullToDefault(businessName, "데이터없음"))
                .businessStatusName(nullToDefault(businessStatusName, "데이터없음"))
                .businessTypeName(nullToDefault(businessTypeName, "데이터없음"))
                .detailStatusName(nullToDefault(detailStatusName, "데이터없음"))
                .fullAddress(nullToDefault(fullAddress, "데이터없음"))
                .hygieneBusinessType(nullToDefault(hygieneBusinessType, "데이터없음"))
                .roadAddress(nullToDefault(roadAddress, "데이터없음"))
                .serviceName(nullToDefault(serviceName, "데이터없음"))
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
        if (fullAddress != null && !fullAddress.trim().isEmpty() && !"데이터없음".equals(fullAddress)) {
            return fullAddress;
        }
        return null;
    }

    /**
     * 영업 중인 숙박업인지 확인
     */
    public boolean isActive() {
        return businessStatusName != null && "영업".equals(businessStatusName.trim());
    }

    /**
     * 폐업한 숙박업인지 확인
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
     * 숙박업 유형별 분류 (호텔/여관/모텔/기타)
     */
    public String getLodgingCategory() {
        if (businessTypeName == null) {
            return "기타";
        }

        String type = businessTypeName.trim().toLowerCase();
        if (type.contains("호텔")) {
            return "호텔";
        } else if (type.contains("여관")) {
            return "여관";
        } else if (type.contains("모텔")) {
            return "모텔";
        } else if (type.contains("펜션")) {
            return "펜션";
        } else if (type.contains("게스트하우스") || type.contains("민박")) {
            return "게스트하우스";
        } else {
            return "기타";
        }
    }

    /**
     * 고급 숙박시설 여부 (호텔, 리조트 등)
     */
    public boolean isHighEndLodging() {
        String category = getLodgingCategory();
        return "호텔".equals(category) ||
                (businessTypeName != null &&
                        (businessTypeName.contains("리조트") ||
                                businessTypeName.contains("특급") ||
                                businessTypeName.contains("5성")));
    }

    /**
     * 관광 숙박시설 여부 (호텔, 펜션, 리조트 등)
     */
    public boolean isTourismLodging() {
        String category = getLodgingCategory();
        return "호텔".equals(category) ||
                "펜션".equals(category) ||
                (businessTypeName != null && businessTypeName.contains("리조트"));
    }

    /**
     * 위생업종 기준 분류
     */
    public String getHygieneCategory() {
        if (hygieneBusinessType == null) {
            return "미분류";
        }

        String type = hygieneBusinessType.trim();
        if (type.contains("여관")) {
            return "여관업";
        } else if (type.contains("관광")) {
            return "관광숙박업";
        } else {
            return type;
        }
    }

    @Override
    public String toString() {
        return String.format("AnalysisLodgingStatistics{id=%d, businessName='%s', type='%s', status='%s', hasCoordinates=%s}",
                id, businessName, businessTypeName, businessStatusName, hasCoordinates());
    }
}