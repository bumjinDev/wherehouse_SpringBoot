package com.wherehouse.AnalysisData.danran.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_DANRAN_BARS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisDanranBars {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_danran_seq")
    @SequenceGenerator(name = "analysis_danran_seq", sequenceName = "SEQ_ANALYSIS_DANRAN_BARS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_CODE")
    private String districtCode;

    @Column(name = "MANAGEMENT_NUMBER")
    private String managementNumber;

    @Column(name = "BUSINESS_STATUS_NAME")
    private String businessStatusName;

    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @Column(name = "JIBUN_ADDRESS")
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "BUSINESS_NAME")
    private String businessName;

    @Column(name = "COORD_X")
    private Double coordX;  // 경도 (Longitude) - Kakao API로 계산

    @Column(name = "COORD_Y")
    private Double coordY;  // 위도 (Latitude) - Kakao API로 계산

    @Column(name = "DISTRICT_NAME")
    private String districtName;  // 구별 분석을 위한 추가 필드 (주소에서 파싱)

    @Column(name = "GEOCODING_STATUS")
    private String geocodingStatus;  // SUCCESS, FAILED, PENDING

    @Column(name = "GEOCODING_ADDRESS_TYPE")
    private String geocodingAddressType;  // JIBUN, ROAD (사용된 주소 타입)

    @Column(name = "API_RESPONSE_ADDRESS")
    private String apiResponseAddress;  // Kakao API 응답으로 받은 정제된 주소
}