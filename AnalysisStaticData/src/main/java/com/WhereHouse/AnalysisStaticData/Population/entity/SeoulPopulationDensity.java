package com.WhereHouse.AnalysisStaticData.Population.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "SEOUL_POPULATION_DENSITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeoulPopulationDensity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "population_seq")
    @SequenceGenerator(name = "population_seq", sequenceName = "SEQ_SEOUL_POPULATION_DENSITY", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_LEVEL1", length = 50)
    private String districtLevel1 = "데이터없음";

    @Column(name = "DISTRICT_LEVEL2", length = 50)
    private String districtLevel2 = "데이터없음";

    @Column(name = "DISTRICT_LEVEL3", length = 50)
    private String districtLevel3 = "데이터없음";

    @Column(name = "YEAR_2024_1")
    private Integer year20241 = 2024;

    @Column(name = "YEAR_2024_2")
    private Integer year20242 = 2024;

    @Column(name = "YEAR_2024_3")
    private Integer year20243 = 2024;

    @Column(name = "POPULATION_COUNT")
    private Long populationCount = 0L;

    @Column(name = "AREA_SIZE", precision = 15, scale = 5)
    private BigDecimal areaSize = BigDecimal.ZERO;

    @Column(name = "POPULATION_DENSITY", precision = 15, scale = 5)
    private BigDecimal populationDensity = BigDecimal.ZERO;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        // NULL 값들을 기본값으로 설정
        if (districtLevel1 == null) districtLevel1 = "데이터없음";
        if (districtLevel2 == null) districtLevel2 = "데이터없음";
        if (districtLevel3 == null) districtLevel3 = "데이터없음";
        if (year20241 == null) year20241 = 2024;
        if (year20242 == null) year20242 = 2024;
        if (year20243 == null) year20243 = 2024;
        if (populationCount == null) populationCount = 0L;
        if (areaSize == null) areaSize = BigDecimal.ZERO;
        if (populationDensity == null) populationDensity = BigDecimal.ZERO;
    }
}