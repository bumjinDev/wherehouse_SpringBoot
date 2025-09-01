package com.WhereHouse.AnalysisData.hospital.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_HOSPITAL_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisHospitalStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_hospital_seq")
    @SequenceGenerator(name = "analysis_hospital_seq", sequenceName = "SEQ_ANALYSIS_HOSPITAL_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "HOSPITAL_NAME")
    private String hospitalName;

    @Column(name = "HOSPITAL_TYPE")
    private String hospitalType;

    @Column(name = "SIDO_NAME")
    private String sidoName;

    @Column(name = "DISTRICT_NAME")
    private String districtName;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "LONGITUDE")
    private BigDecimal longitude;

    @Column(name = "LATITUDE")
    private BigDecimal latitude;
}