package com.WhereHouse.AnalysisData.mart.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "ANALYSIS_MART_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisMartStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_mart_seq")
    @SequenceGenerator(name = "analysis_mart_seq", sequenceName = "SEQ_ANALYSIS_MART_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "LOCAL_GOVT_CODE")
    private String localGovtCode;

    @Column(name = "MANAGEMENT_NO")
    private String managementNo;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

    @Column(name = "BUSINESS_STATUS_CODE")
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME")
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE")
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME")
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @Column(name = "LOCATION_AREA")
    private Double locationArea;

    @Column(name = "LOCATION_POSTAL_CODE")
    private String locationPostalCode;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE")
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME")
    private String businessName;

    @Column(name = "LAST_UPDATE_DATE")
    private LocalDate lastUpdateDate;

    @Column(name = "DATA_UPDATE_TYPE")
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE")
    private String dataUpdateDate;

    @Column(name = "BUSINESS_TYPE_NAME")
    private String businessTypeName;

    @Column(name = "STORE_TYPE_NAME")
    private String storeTypeName;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}