package com.wherehouse.AnalysisData.university.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "ANALYSIS_UNIVERSITY_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisUniversityStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_university_seq")
    @SequenceGenerator(name = "analysis_university_seq", sequenceName = "SEQ_ANALYSIS_UNIVERSITY_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHOOL_NAME")
    private String schoolName;

    @Column(name = "SCHOOL_NAME_ENG")
    private String schoolNameEng;

    @Column(name = "MAIN_BRANCH_TYPE")
    private String mainBranchType;

    @Column(name = "UNIVERSITY_TYPE")
    private String universityType;

    @Column(name = "SCHOOL_TYPE")
    private String schoolType;

    @Column(name = "ESTABLISHMENT_TYPE")
    private String establishmentType;

    @Column(name = "SIDO_CODE")
    private String sidoCode;

    @Column(name = "SIDO_NAME")
    private String sidoName;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE")
    private String roadPostalCode;

    @Column(name = "HOMEPAGE_URL")
    private String homepageUrl;

    @Column(name = "MAIN_PHONE")
    private String mainPhone;

    @Column(name = "MAIN_FAX")
    private String mainFax;

    @Column(name = "ESTABLISHMENT_DATE")
    private LocalDate establishmentDate;

    @Column(name = "BASE_YEAR")
    private Integer baseYear;

    @Column(name = "DATA_BASE_DATE")
    private LocalDate dataBaseDate;

    @Column(name = "PROVIDER_CODE")
    private String providerCode;

    @Column(name = "PROVIDER_NAME")
    private String providerName;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}