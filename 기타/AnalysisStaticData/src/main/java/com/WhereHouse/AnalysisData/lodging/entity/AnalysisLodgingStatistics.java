package com.WhereHouse.AnalysisData.lodging.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_LODGING_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisLodgingStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_lodging_seq")
    @SequenceGenerator(name = "analysis_lodging_seq", sequenceName = "SEQ_ANALYSIS_LODGING_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BUILDING_OWNERSHIP_TYPE")
    private String buildingOwnershipType;

    @Column(name = "BUSINESS_NAME")
    private String businessName;

    @Column(name = "BUSINESS_STATUS_NAME")
    private String businessStatusName;

    @Column(name = "BUSINESS_TYPE_NAME")
    private String businessTypeName;

    @Column(name = "DETAIL_STATUS_NAME")
    private String detailStatusName;

    @Column(name = "FULL_ADDRESS")
    private String fullAddress;

    @Column(name = "HYGIENE_BUSINESS_TYPE")
    private String hygieneBusinessType;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "SERVICE_NAME")
    private String serviceName;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}