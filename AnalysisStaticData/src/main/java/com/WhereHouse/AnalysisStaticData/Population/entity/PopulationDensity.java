package com.WhereHouse.AnalysisStaticData.Population.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "POPULATION_DENSITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopulationDensity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "population_seq")
    @SequenceGenerator(
            name = "population_seq",
            sequenceName = "SEQ_POPULATION_DENSITY",
            allocationSize = 1
    )
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_NAME", length = 100, nullable = false)
    private String districtName;

    @Column(name = "YEAR", nullable = false)
    private Integer year;

    @Column(name = "POPULATION_COUNT")
    private Long populationCount;

    @Column(name = "AREA_SIZE", precision = 15, scale = 5)
    private BigDecimal areaSize;

    @Column(name = "POPULATION_DENSITY", precision = 15, scale = 5)
    private BigDecimal populationDensity;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.year == null) {
            this.year = 2024;
        }
    }

    // 인구밀도 계산 메서드
    public void calculateDensity() {
        if (populationCount != null && areaSize != null &&
                areaSize.compareTo(BigDecimal.ZERO) > 0) {
            this.populationDensity = new BigDecimal(populationCount)
                    .divide(areaSize, 5, BigDecimal.ROUND_HALF_UP);
        }
    }

    // 편의 메서드
    public String getFormattedPopulationCount() {
        return populationCount != null ? String.format("%,d명", populationCount) : "0명";
    }

    public String getFormattedAreaSize() {
        return areaSize != null ? String.format("%.2f㎢", areaSize) : "0.00㎢";
    }

    public String getFormattedPopulationDensity() {
        return populationDensity != null ? String.format("%.0f명/㎢", populationDensity) : "0명/㎢";
    }
}