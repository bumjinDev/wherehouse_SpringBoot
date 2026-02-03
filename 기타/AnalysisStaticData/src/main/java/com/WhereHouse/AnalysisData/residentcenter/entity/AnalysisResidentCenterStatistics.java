package com.WhereHouse.AnalysisData.residentcenter.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_RESIDENT_CENTER_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResidentCenterStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_resident_center_seq")
    @SequenceGenerator(name = "analysis_resident_center_seq", sequenceName = "SEQ_ANALYSIS_RESIDENT_CENTER_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SIDO")
    private String sido;

    @Column(name = "SIGUNGU")
    private String sigungu;

    @Column(name = "EUPMEONDONG")
    private String eupmeondong;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}