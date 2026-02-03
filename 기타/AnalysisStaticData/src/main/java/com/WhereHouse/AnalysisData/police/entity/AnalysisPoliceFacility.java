package com.WhereHouse.AnalysisData.police.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_POLICE_FACILITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisPoliceFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_police_seq")
    @SequenceGenerator(name = "analysis_police_seq", sequenceName = "SEQ_ANALYSIS_POLICE_FACILITY", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERIAL_NO")
    private Integer serialNo;

    @Column(name = "CITY_PROVINCE")
    private String cityProvince;

    @Column(name = "POLICE_STATION")
    private String policeStation;

    @Column(name = "FACILITY_NAME")
    private String facilityName;

    @Column(name = "FACILITY_TYPE")
    private String facilityType;

    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "COORD_X")
    private Double coordX;  // 경도 (Longitude) - Kakao API로 계산

    @Column(name = "COORD_Y")
    private Double coordY;  // 위도 (Latitude) - Kakao API로 계산

    @Column(name = "DISTRICT_NAME")
    private String districtName;  // 구별 분석을 위한 추가 필드 (주소에서 파싱)

    @Column(name = "GEOCODING_STATUS")
    private String geocodingStatus;  // SUCCESS, FAILED, PENDING

    @Column(name = "API_RESPONSE_ADDRESS")
    private String apiResponseAddress;  // Kakao API 응답으로 받은 정제된 주소
}