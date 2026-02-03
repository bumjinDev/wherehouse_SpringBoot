package com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SCHOOL_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "school_seq")
    @SequenceGenerator(name = "school_seq", sequenceName = "SEQ_SCHOOL_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SCHOOL_ID", nullable = false, length = 20)
    private String schoolId;

    @Column(name = "SCHOOL_NAME", nullable = false, length = 100)
    private String schoolName;

    @Column(name = "SCHOOL_LEVEL", nullable = false, length = 20)
    private String schoolLevel;

    @Column(name = "ESTABLISHMENT_DATE")
    private LocalDate establishmentDate;

    @Column(name = "ESTABLISHMENT_TYPE", length = 20)
    private String establishmentType;

    @Column(name = "MAIN_BRANCH_TYPE", length = 10)
    private String mainBranchType;

    @Column(name = "OPERATION_STATUS", length = 20)
    private String operationStatus;

    @Column(name = "LOCATION_ADDRESS", length = 200)
    private String locationAddress;

    @Column(name = "ROAD_ADDRESS", length = 200)
    private String roadAddress;

    @Column(name = "EDUCATION_OFFICE_CODE", length = 20)
    private String educationOfficeCode;

    @Column(name = "EDUCATION_OFFICE_NAME", length = 50)
    private String educationOfficeName;

    @Column(name = "SUPPORT_OFFICE_CODE", length = 20)
    private String supportOfficeCode;

    @Column(name = "SUPPORT_OFFICE_NAME", length = 50)
    private String supportOfficeName;

    @Column(name = "CREATE_DATE")
    private LocalDate createDate;

    @Column(name = "MODIFY_DATE")
    private LocalDate modifyDate;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

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