package com.wherehouse.AnalysisData.residentcenter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 주민센터 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(행정시설 접근성) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_RESIDENT_CENTER_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResidentCenterStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_resident_center_seq")
    @SequenceGenerator(name = "analysis_resident_center_seq",
            sequenceName = "SEQ_ANALYSIS_RESIDENT_CENTER_STATISTICS",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SIDO", length = 4000)
    private String sido;                        // 시도

    @Column(name = "SIGUNGU", length = 4000)
    private String sigungu;                     // 시군구

    @Column(name = "EUPMEONDONG", length = 4000)
    private String eupmeondong;                 // 읍면동

    @Column(name = "ADDRESS", length = 4000)
    private String address;                     // 주소

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                // 위도

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;               // 경도

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisResidentCenterStatistics fromSourceData(
            String sido,
            String sigungu,
            String eupmeondong,
            String address) {

        return AnalysisResidentCenterStatistics.builder()
                .sido(nullToDefault(sido, "데이터없음"))
                .sigungu(nullToDefault(sigungu, "데이터없음"))
                .eupmeondong(nullToDefault(eupmeondong, "데이터없음"))
                .address(nullToDefault(address, "데이터없음"))
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
     * 서울시 자치구 여부 확인
     */
    public boolean isSeoulDistrict() {
        return sido != null && sido.contains("서울") &&
                sigungu != null && sigungu.contains("구");
    }

    /**
     * 정규화된 자치구명 반환 (공백 제거 등)
     */
    public String getNormalizedSigungu() {
        if (sigungu == null) {
            return null;
        }

        // "중 구" -> "중구" 같은 공백 제거
        String normalized = sigungu.replaceAll("\\s+", "");

        // 구 이름만 추출
        if (normalized.endsWith("구")) {
            return normalized;
        }

        return sigungu.trim();
    }

    /**
     * 읍면동 유형 반환
     */
    public String getEupmeondongType() {
        if (eupmeondong == null) {
            return "미분류";
        }

        String type = eupmeondong.trim();
        if (type.endsWith("읍")) {
            return "읍";
        } else if (type.endsWith("면")) {
            return "면";
        } else if (type.endsWith("동")) {
            return "동";
        } else {
            return "기타";
        }
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
        return address != null && !address.trim().isEmpty() && !"데이터없음".equals(address);
    }

    /**
     * 완전한 행정구역 정보 보유 여부
     */
    public boolean hasCompleteAdministrativeInfo() {
        return sido != null && !"데이터없음".equals(sido) &&
                sigungu != null && !"데이터없음".equals(sigungu) &&
                eupmeondong != null && !"데이터없음".equals(eupmeondong);
    }

    /**
     * 전체 행정구역 경로 문자열 반환
     */
    public String getFullAdministrativePath() {
        if (!hasCompleteAdministrativeInfo()) {
            return "정보불완전";
        }

        return String.format("%s %s %s",
                sido != null ? sido : "",
                getNormalizedSigungu() != null ? getNormalizedSigungu() : "",
                eupmeondong != null ? eupmeondong : "").trim();
    }

    @Override
    public String toString() {
        return String.format("AnalysisResidentCenterStatistics{id=%d, sigungu='%s', eupmeondong='%s', hasCoordinates=%s}",
                id, getNormalizedSigungu(), eupmeondong, hasCoordinates());
    }
}