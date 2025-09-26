package com.aws.database.Population.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_POPULATION_DENSITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisPopulationDensity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_population_seq")
    @SequenceGenerator(name = "analysis_population_seq", sequenceName = "SEQ_ANALYSIS_POPULATION_DENSITY", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_NAME")
    private String districtName;

    @Column(name = "YEAR")
    private Integer year;

    @Column(name = "POPULATION_COUNT")
    private Long populationCount;

    @Column(name = "AREA_SIZE")
    private BigDecimal areaSize;

    @Column(name = "POPULATION_DENSITY")
    private BigDecimal populationDensity;
}