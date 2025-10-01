package com.aws.database.CRIME.domain;

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

    @Column(name = "DISTRICT_NAME")
    private String districtName;

    @Column(name = "YEAR")
    private Integer year;

    @Column(name = "TOTAL_OCCURRENCE")
    private Integer totalOccurrence;

    @Column(name = "TOTAL_ARREST")
    private Integer totalArrest;

    @Column(name = "MURDER_OCCURRENCE")
    private Integer murderOccurrence;

    @Column(name = "MURDER_ARREST")
    private Integer murderArrest;

    @Column(name = "ROBBERY_OCCURRENCE")
    private Integer robberyOccurrence;

    @Column(name = "ROBBERY_ARREST")
    private Integer robberyArrest;

    @Column(name = "SEXUAL_CRIME_OCCURRENCE")
    private Integer sexualCrimeOccurrence;

    @Column(name = "SEXUAL_CRIME_ARREST")
    private Integer sexualCrimeArrest;

    @Column(name = "THEFT_OCCURRENCE")
    private Integer theftOccurrence;

    @Column(name = "THEFT_ARREST")
    private Integer theftArrest;

    @Column(name = "VIOLENCE_OCCURRENCE")
    private Integer violenceOccurrence;

    @Column(name = "VIOLENCE_ARREST")
    private Integer violenceArrest;
}