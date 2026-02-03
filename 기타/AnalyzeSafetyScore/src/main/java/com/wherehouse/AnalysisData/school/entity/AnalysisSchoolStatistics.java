package com.wherehouse.AnalysisData.school.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 학교 데이터 분석용 Entity
 * 피어슨 상관분석에서 독립변수(교육시설 밀도) 데이터로 활용
 */
@Entity
@Table(name = "ANALYSIS_SCHOOL_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSchoolStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_school_seq")
    @SequenceGenerator(name = "analysis_school_seq",
            sequenceName = "SEQ_ANALYSIS_SCHOOL_STATISTICS",
            allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHOOL_ID", length = 4000)
    private String schoolId;                    // 학교ID

    @Column(name = "SCHOOL_NAME", length = 4000)
    private String schoolName;                  // 학교명

    @Column(name = "SCHOOL_LEVEL", length = 4000)
    private String schoolLevel;                 // 학교급 (초등학교, 중학교, 고등학교 등)

    @Column(name = "ESTABLISHMENT_TYPE", length = 4000)
    private String establishmentType;           // 설립유형 (공립, 사립 등)

    @Column(name = "MAIN_BRANCH_TYPE", length = 4000)
    private String mainBranchType;              // 본분교구분

    @Column(name = "OPERATION_STATUS", length = 4000)
    private String operationStatus;             // 운영상태

    @Column(name = "LOCATION_ADDRESS", length = 4000)
    private String locationAddress;             // 지번주소

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;                 // 도로명주소

    @Column(name = "EDUCATION_OFFICE_NAME", length = 4000)
    private String educationOfficeName;         // 교육청명

    @Column(name = "SUPPORT_OFFICE_NAME", length = 4000)
    private String supportOfficeName;           // 교육지원청명

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;                // 위도

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;               // 경도

    @Column(name = "PROVIDER_NAME", length = 4000)
    private String providerName;                // 제공기관명

    /**
     * 원천 데이터에서 분석용 데이터로 변환하는 정적 팩토리 메서드
     * NULL 값 처리 규칙을 적용하여 변환
     */
    public static AnalysisSchoolStatistics fromSourceData(
            String schoolId,
            String schoolName,
            String schoolLevel,
            String establishmentType,
            String mainBranchType,
            String operationStatus,
            String locationAddress,
            String roadAddress,
            String educationOfficeName,
            String supportOfficeName,
            String providerName) {

        return AnalysisSchoolStatistics.builder()
                .schoolId(nullToDefault(schoolId, "데이터없음"))
                .schoolName(nullToDefault(schoolName, "데이터없음"))
                .schoolLevel(nullToDefault(schoolLevel, "데이터없음"))
                .establishmentType(nullToDefault(establishmentType, "데이터없음"))
                .mainBranchType(nullToDefault(mainBranchType, "데이터없음"))
                .operationStatus(nullToDefault(operationStatus, "데이터없음"))
                .locationAddress(nullToDefault(locationAddress, "데이터없음"))
                .roadAddress(nullToDefault(roadAddress, "데이터없음"))
                .educationOfficeName(nullToDefault(educationOfficeName, "데이터없음"))
                .supportOfficeName(nullToDefault(supportOfficeName, "데이터없음"))
                .providerName(nullToDefault(providerName, "데이터없음"))
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
        if (locationAddress != null && !locationAddress.trim().isEmpty() && !"데이터없음".equals(locationAddress)) {
            return locationAddress;
        }
        return null;
    }

    /**
     * 운영 중인 학교인지 확인
     */
    public boolean isActive() {
        return operationStatus != null && "운영".equals(operationStatus.trim());
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
     * 학교급별 분류 (초/중/고/기타)
     */
    public String getSchoolCategory() {
        if (schoolLevel == null) {
            return "기타";
        }

        String level = schoolLevel.trim();
        if (level.contains("초등")) {
            return "초등학교";
        } else if (level.contains("중학")) {
            return "중학교";
        } else if (level.contains("고등")) {
            return "고등학교";
        } else if (level.contains("특수")) {
            return "특수학교";
        } else {
            return "기타";
        }
    }

    @Override
    public String toString() {
        return String.format("AnalysisSchoolStatistics{id=%d, schoolName='%s', level='%s', status='%s', hasCoordinates=%s}",
                id, schoolName, schoolLevel, operationStatus, hasCoordinates());
    }
}