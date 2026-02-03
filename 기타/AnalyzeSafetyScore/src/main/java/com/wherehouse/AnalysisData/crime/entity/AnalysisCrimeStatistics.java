package com.wherehouse.AnalysisData.crime.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_CRIME_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisCrimeStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_crime_seq")
    @SequenceGenerator(name = "analysis_crime_seq", sequenceName = "SEQ_ANALYSIS_CRIME_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_NAME", length = 50)
    private String districtName;

    @Column(name = "YEAR")
    private Long year;

    @Column(name = "TOTAL_OCCURRENCE")
    private Long totalOccurrence;

    @Column(name = "TOTAL_ARREST")
    private Long totalArrest;

    @Column(name = "MURDER_OCCURRENCE")
    private Long murderOccurrence;

    @Column(name = "MURDER_ARREST")
    private Long murderArrest;

    @Column(name = "ROBBERY_OCCURRENCE")
    private Long robberyOccurrence;

    @Column(name = "ROBBERY_ARREST")
    private Long robberyArrest;

    @Column(name = "SEXUAL_CRIME_OCCURRENCE")
    private Long sexualCrimeOccurrence;

    @Column(name = "SEXUAL_CRIME_ARREST")
    private Long sexualCrimeArrest;

    @Column(name = "THEFT_OCCURRENCE")
    private Long theftOccurrence;

    @Column(name = "THEFT_ARREST")
    private Long theftArrest;

    @Column(name = "VIOLENCE_OCCURRENCE")
    private Long violenceOccurrence;

    @Column(name = "VIOLENCE_ARREST")
    private Long violenceArrest;
}