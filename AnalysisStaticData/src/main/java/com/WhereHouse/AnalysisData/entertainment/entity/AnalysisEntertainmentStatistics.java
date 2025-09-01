package com.WhereHouse.AnalysisData.entertainment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_ENTERTAINMENT_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisEntertainmentStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_entertainment_seq")
    @SequenceGenerator(name = "analysis_entertainment_seq", sequenceName = "SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

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

    @Column(name = "BUSINESS_CATEGORY")
    private String businessCategory;

    @Column(name = "HYGIENE_BUSINESS_TYPE")
    private String hygieneBusinessType;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}