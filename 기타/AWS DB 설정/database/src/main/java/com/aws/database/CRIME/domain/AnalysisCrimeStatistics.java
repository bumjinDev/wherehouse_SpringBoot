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

    // ... (제공된 파일의 나머지 필드들을 여기에 포함) ...

    @Column(name = "VIOLENCE_ARREST")
    private Integer violenceArrest;
}