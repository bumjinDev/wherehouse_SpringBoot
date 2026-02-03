package com.WhereHouse.AnalysisStaticData.University.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "UNIVERSITY_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniversityStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "university_seq")
    @SequenceGenerator(name = "university_seq", sequenceName = "SEQ_UNIVERSITY_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHOOL_NAME", nullable = false, length = 100)
    private String schoolName;

    @Column(name = "SCHOOL_NAME_ENG", length = 200)
    private String schoolNameEng;

    @Column(name = "MAIN_BRANCH_TYPE", length = 20)
    private String mainBranchType;

    @Column(name = "UNIVERSITY_TYPE", length = 20)
    private String universityType;

    @Column(name = "SCHOOL_TYPE", length = 30)
    private String schoolType;

    @Column(name = "ESTABLISHMENT_TYPE", length = 20)
    private String establishmentType;

    @Column(name = "SIDO_CODE", length = 10)
    private String sidoCode;

    @Column(name = "SIDO_NAME", length = 30)
    private String sidoName;

    @Column(name = "ROAD_ADDRESS", length = 300)
    private String roadAddress;

    @Column(name = "LOCATION_ADDRESS", length = 300)
    private String locationAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 10)
    private String roadPostalCode;

    @Column(name = "LOCATION_POSTAL_CODE", length = 10)
    private String locationPostalCode;

    @Column(name = "HOMEPAGE_URL", length = 200)
    private String homepageUrl;

    @Column(name = "MAIN_PHONE", length = 20)
    private String mainPhone;

    @Column(name = "MAIN_FAX", length = 20)
    private String mainFax;

    @Column(name = "ESTABLISHMENT_DATE")
    private LocalDate establishmentDate;

    @Column(name = "BASE_YEAR")
    private Integer baseYear;

    @Column(name = "DATA_BASE_DATE")
    private LocalDate dataBaseDate;

    @Column(name = "PROVIDER_CODE", length = 20)
    private String providerCode;

    @Column(name = "PROVIDER_NAME", length = 50)
    private String providerName;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}