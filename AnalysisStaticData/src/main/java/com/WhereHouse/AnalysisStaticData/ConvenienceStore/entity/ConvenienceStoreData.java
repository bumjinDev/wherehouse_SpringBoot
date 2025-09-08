package com.WhereHouse.AnalysisStaticData.ConvenienceStore.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_CONVENIENCE_STORE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisConvenienceStore {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_convenience_seq")
    @SequenceGenerator(name = "analysis_convenience_seq", sequenceName = "SEQ_ANALYSIS_CONVENIENCE_STORE", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DETAILED_STATUS_NAME")
    private String detailedStatusName;

    @Column(name = "LOT_ADDRESS")
    private String lotAddress;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "BUSINESS_NAME")
    private String businessName;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}