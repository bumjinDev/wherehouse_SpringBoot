package com.wherehouse.AnalysisData.pcbang.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_PC_BANG_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisPcBangStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_pc_bang_seq")
    @SequenceGenerator(name = "analysis_pc_bang_seq", sequenceName = "SEQ_ANALYSIS_PC_BANG_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_CODE", length = 20)
    private String districtCode;

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber;

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName;

    @Column(name = "JIBUN_ADDRESS", length = 1000)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 1000)
    private String roadAddress;

    @Column(name = "BUSINESS_NAME", length = 500)
    private String businessName;

    @Column(name = "LATITUDE", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 10, scale = 8)
    private BigDecimal longitude;

    @Column(name = "GEOCODING_STATUS", length = 4000)
    private String geocodingStatus;
}