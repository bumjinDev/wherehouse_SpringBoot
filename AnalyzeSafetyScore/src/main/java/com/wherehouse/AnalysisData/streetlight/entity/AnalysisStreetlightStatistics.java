package com.wherehouse.AnalysisData.streetlight.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_STREETLIGHT_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisStreetlightStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_streetlight_seq")
    @SequenceGenerator(name = "analysis_streetlight_seq", sequenceName = "SEQ_ANALYSIS_STREETLIGHT_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MANAGEMENT_NUMBER")
    private String managementNumber;

    @Column(name = "DISTRICT_NAME")
    private String districtName;

    @Column(name = "DONG_NAME")
    private String dongName;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "JIBUN_ADDRESS")
    private String jibunAddress;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}