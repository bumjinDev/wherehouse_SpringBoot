package com.WhereHouse.AnalysisStaticData.CriminalInfoSave.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "CRIME_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrimeStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "crime_seq")
    @SequenceGenerator(name = "crime_seq", sequenceName = "SEQ_CRIME_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_NAME", nullable = false, length = 50)
    private String districtName;

    @Column(name = "YEAR")
    private Integer year = 2023;

    @Column(name = "TOTAL_OCCURRENCE")
    private Integer totalOccurrence = 0;

    @Column(name = "TOTAL_ARREST")
    private Integer totalArrest = 0;

    @Column(name = "MURDER_OCCURRENCE")
    private Integer murderOccurrence = 0;

    @Column(name = "MURDER_ARREST")
    private Integer murderArrest = 0;

    @Column(name = "ROBBERY_OCCURRENCE")
    private Integer robberyOccurrence = 0;

    @Column(name = "ROBBERY_ARREST")
    private Integer robberyArrest = 0;

    @Column(name = "SEXUAL_CRIME_OCCURRENCE")
    private Integer sexualCrimeOccurrence = 0;

    @Column(name = "SEXUAL_CRIME_ARREST")
    private Integer sexualCrimeArrest = 0;

    @Column(name = "THEFT_OCCURRENCE")
    private Integer theftOccurrence = 0;

    @Column(name = "THEFT_ARREST")
    private Integer theftArrest = 0;

    @Column(name = "VIOLENCE_OCCURRENCE")
    private Integer violenceOccurrence = 0;

    @Column(name = "VIOLENCE_ARREST")
    private Integer violenceArrest = 0;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}