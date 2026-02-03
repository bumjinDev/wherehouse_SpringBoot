package com.WhereHouse.AnalysisData.cinema.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_CINEMA_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisCinemaStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_cinema_seq")
    @SequenceGenerator(name = "analysis_cinema_seq", sequenceName = "SEQ_ANALYSIS_CINEMA_STATISTICS", allocationSize = 1)
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

    @Column(name = "CULTURE_SPORTS_TYPE_NAME")
    private String cultureSportsTypeName;

    @Column(name = "BUILDING_USE_NAME")
    private String buildingUseName;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}