package com.wherehouse.AnalysisData.mart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 마트 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(상업시설 밀도) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_MART_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisMartStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_mart_seq")
    @SequenceGenerator(name = "analysis_mart_seq",
            sequenceName = "SEQ_ANALYSIS_MART_STATISTICS",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BUSINESS_STATUS_NAME", length = 4000)
    private String businessStatusName;          // 영업상태명

    @Column(name = "PHONE_NUMBER", length = 4000)
    private String phoneNumber;                 // 전화번호

    @Column(name = "ADDRESS", length = 4000)
    private String address;                     // 지번주소

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;                 // 도로명주소

    @Column(name = "BUSINESS_NAME", length = 4000)
    private String businessName;                // 업체명

    @Column(name = "BUSINESS_TYPE_NAME", length = 4000)
    private String businessTypeName;            // 업종명

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                // 위도

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;               // 경도

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisMartStatistics fromSourceData(
            String businessStatusName,
            String phoneNumber,
            String address,
            String roadAddress,
            String businessName,
            String businessTypeName) {

        return AnalysisMartStatistics.builder()
                .businessStatusName(nullToDefault(businessStatusName, "데이터없음"))
                .phoneNumber(nullToDefault(phoneNumber, "데이터없음"))
                .address(nullToDefault(address, "데이터없음"))
                .roadAddress(nullToDefault(roadAddress, "데이터없음"))
                .businessName(nullToDefault(businessName, "데이터없음"))
                .businessTypeName(nullToDefault(businessTypeName, "데이터없음"))
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
        if (address != null && !address.trim().isEmpty() && !"데이터없음".equals(address)) {
            return address;
        }
        return null;
    }

    /**
     * 영업 중인 마트인지 확인
     */
    public boolean isActive() {
        return businessStatusName != null && "영업".equals(businessStatusName.trim());
    }

    /**
     * 폐업한 마트인지 확인
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
     * 마트 유형별 분류 (백화점/대형마트/슈퍼마켓/기타)
     */
    public String getMartCategory() {
        if (businessTypeName == null) {
            return "기타";
        }

        String type = businessTypeName.trim();
        if (type.contains("백화점")) {
            return "백화점";
        } else if (type.contains("대형마트") || type.contains("할인점")) {
            return "대형마트";
        } else if (type.contains("슈퍼마켓") || type.contains("슈퍼")) {
            return "슈퍼마켓";
        } else if (type.contains("마트")) {
            return "마트";
        } else {
            return "기타";
        }
    }

    /**
     * 대형 상업시설 여부 (백화점, 대형마트)
     */
    public boolean isLargeScale() {
        String category = getMartCategory();
        return "백화점".equals(category) || "대형마트".equals(category);
    }

    @Override
    public String toString() {
        return String.format("AnalysisMartStatistics{id=%d, businessName='%s', type='%s', status='%s', hasCoordinates=%s}",
                id, businessName, businessTypeName, businessStatusName, hasCoordinates());
    }
}