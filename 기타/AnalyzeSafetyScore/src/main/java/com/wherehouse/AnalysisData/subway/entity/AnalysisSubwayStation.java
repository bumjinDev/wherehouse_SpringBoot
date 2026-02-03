package com.wherehouse.AnalysisData.subway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 지하철역 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(대중교통 접근성) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_SUBWAY_STATION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSubwayStation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_subway_seq")
    @SequenceGenerator(name = "analysis_subway_seq",
            sequenceName = "SEQ_ANALYSIS_SUBWAY_STATION",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "STATION_NAME_KOR", length = 4000)
    private String stationNameKor;              // 역명(한글)

    @Column(name = "STATION_PHONE", length = 4000)
    private String stationPhone;                // 역 전화번호

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;                 // 도로명주소

    @Column(name = "JIBUN_ADDRESS", length = 4000)
    private String jibunAddress;                // 지번주소

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                // 위도

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;               // 경도

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisSubwayStation fromSourceData(
            String stationNameKor,
            String stationPhone,
            String roadAddress,
            String jibunAddress) {

        return AnalysisSubwayStation.builder()
                .stationNameKor(nullToDefault(stationNameKor, "데이터없음"))
                .stationPhone(nullToDefault(stationPhone, "데이터없음"))
                .roadAddress(nullToDefault(roadAddress, "데이터없음"))
                .jibunAddress(nullToDefault(jibunAddress, "데이터없음"))
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
     * 역명에서 라인 정보 추출 (예: "강남(2호선)")
     */
    public String extractLineInfo() {
        if (stationNameKor == null) {
            return "정보없음";
        }

        String name = stationNameKor.trim();
        if (name.contains("(") && name.contains(")")) {
            int start = name.indexOf("(");
            int end = name.indexOf(")", start);
            if (end > start) {
                return name.substring(start + 1, end);
            }
        }
        return "정보없음";
    }

    /**
     * 순수 역명 반환 (라인 정보 제외)
     */
    public String getPureStationName() {
        if (stationNameKor == null) {
            return "데이터없음";
        }

        String name = stationNameKor.trim();
        if (name.contains("(")) {
            return name.substring(0, name.indexOf("(")).trim();
        }
        return name;
    }

    /**
     * 환승역 여부 확인 (역명에 여러 호선 정보가 있는지 확인)
     */
    public boolean isTransferStation() {
        String lineInfo = extractLineInfo();
        return lineInfo.contains(",") || lineInfo.contains("호선");
    }

    /**
     * 연락처 정보 보유 여부
     */
    public boolean hasContactInfo() {
        return stationPhone != null &&
                !stationPhone.trim().isEmpty() &&
                !"데이터없음".equals(stationPhone) &&
                stationPhone.matches(".*\\d+.*"); // 숫자가 포함된 경우만
    }

    /**
     * 지하철역의 대중교통 기여도 점수 (1-10)
     */
    public int getPublicTransportContributionScore() {
        int baseScore = 5; // 기본 점수

        // 환승역이면 점수 추가
        if (isTransferStation()) {
            baseScore += 3;
        }

        // 좌표 정보가 있으면 점수 추가
        if (hasCoordinates()) {
            baseScore += 1;
        }

        // 연락처 정보가 있으면 점수 추가
        if (hasContactInfo()) {
            baseScore += 1;
        }

        return Math.min(baseScore, 10);
    }

    @Override
    public String toString() {
        return String.format("AnalysisSubwayStation{id=%d, stationName='%s', lineInfo='%s', hasCoordinates=%s, isTransfer=%s}",
                id, getPureStationName(), extractLineInfo(), hasCoordinates(), isTransferStation());
    }
}