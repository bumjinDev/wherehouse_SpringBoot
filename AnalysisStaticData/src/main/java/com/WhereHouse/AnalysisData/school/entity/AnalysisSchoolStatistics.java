package com.wherehouse.AnalysisData.school.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_SCHOOL_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSchoolStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_school_seq")
    @SequenceGenerator(name = "analysis_school_seq", sequenceName = "SEQ_ANALYSIS_SCHOOL_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHOOL_ID")
    private String schoolId;

    @Column(name = "SCHOOL_NAME")
    private String schoolName;

    @Column(name = "SCHOOL_LEVEL")
    private String schoolLevel;

    @Column(name = "ESTABLISHMENT_TYPE")
    private String establishmentType;

    @Column(name = "MAIN_BRANCH_TYPE")
    private String mainBranchType;

    @Column(name = "OPERATION_STATUS")
    private String operationStatus;

    @Column(name = "LOCATION_ADDRESS")
    private String locationAddress;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "EDUCATION_OFFICE_NAME")
    private String educationOfficeName;

    @Column(name = "SUPPORT_OFFICE_NAME")
    private String supportOfficeName;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Column(name = "PROVIDER_NAME")
    private String providerName;
}