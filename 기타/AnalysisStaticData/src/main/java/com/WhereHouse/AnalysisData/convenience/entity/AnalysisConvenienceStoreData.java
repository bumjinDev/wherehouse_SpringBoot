package com.WhereHouse.AnalysisData.convenience.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 편의점 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(상권 밀도) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_CONVENIENCE_STORE_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisConvenienceStoreData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_convenience_seq")
    @SequenceGenerator(name = "analysis_convenience_seq",
            sequenceName = "SEQ_ANALYSIS_CONVENIENCE_STORE_DATA",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BUSINESS_NAME", length = 4000)
    private String businessName;                        // 사업장명

    @Column(name = "DETAILED_STATUS_NAME", length = 4000)
    private String detailedStatusName;                  // 상세영업상태명

    @Column(name = "PHONE_NUMBER", length = 4000)
    private String phoneNumber;                         // 전화번호

    @Column(name = "LOT_ADDRESS", length = 4000)
    private String lotAddress;                          // 지번주소

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;                         // 도로명주소

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                        // 위도 (Kakao API 계산)

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;                       // 경도 (Kakao API 계산)

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisConvenienceStoreData fromSourceData(
            String businessName,
            String detailedStatusName,
            String phoneNumber,
            String lotAddress,
            String roadAddress) {

        return AnalysisConvenienceStoreData.builder()
                .businessName(nullToDefault(businessName, "데이터없음"))
                .detailedStatusName(nullToDefault(detailedStatusName, "데이터없음"))
                .phoneNumber(nullToDefault(phoneNumber, "데이터없음"))
                .lotAddress(nullToDefault(lotAddress, "데이터없음"))
                .roadAddress(nullToDefault(roadAddress, "데이터없음"))
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
        if (lotAddress != null && !lotAddress.trim().isEmpty() && !"데이터없음".equals(lotAddress)) {
            return lotAddress;
        }
        return null;
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

    @Override
    public String toString() {
        return String.format("AnalysisConvenienceStoreData{id=%d, businessName='%s', status='%s', hasCoordinates=%s}",
                id, businessName, detailedStatusName, hasCoordinates());
    }
}