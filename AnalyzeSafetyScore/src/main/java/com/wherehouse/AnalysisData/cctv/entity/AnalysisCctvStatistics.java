package com.wherehouse.AnalysisData.cctv.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_CCTV_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisCctvStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_cctv_seq")
    @SequenceGenerator(name = "analysis_cctv_seq", sequenceName = "SEQ_ANALYSIS_CCTV_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MANAGEMENT_AGENCY", length = 100)
    private String managementAgency;

    @Column(name = "ROAD_ADDRESS", length = 200)
    private String roadAddress;

    @Column(name = "JIBUN_ADDRESS", length = 200)
    private String jibunAddress;

    @Column(name = "LATITUDE", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 10, scale = 8)
    private BigDecimal longitude;
}