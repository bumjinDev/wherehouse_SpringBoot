package com.WhereHouse.AnalysisStaticData.EntertainmentBar.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ENTERTAINMENT_BARS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntertainmentBars {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "entertainment_seq")
    @SequenceGenerator(name = "entertainment_seq", sequenceName = "SEQ_ENTERTAINMENT_BARS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DISTRICT_CODE", nullable = false, length = 20)
    private String districtCode;

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "CANCEL_DATE")
    private LocalDate cancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 20)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE", length = 20)
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME", length = 100)
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPEND_START_DATE")
    private LocalDate suspendStartDate;

    @Column(name = "SUSPEND_END_DATE")
    private LocalDate suspendEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "AREA_SIZE", precision = 10, scale = 2)
    private BigDecimal areaSize;

    @Column(name = "POSTAL_CODE", length = 20)
    private String postalCode;

    @Column(name = "JIBUN_ADDRESS", length = 1000)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 1000)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 500)
    private String businessName;

    @Column(name = "LAST_MODIFIED_DATE")
    private LocalDateTime lastModifiedDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE")
    private LocalDateTime dataUpdateDate;

    @Column(name = "BUSINESS_CATEGORY", length = 100)
    private String businessCategory;

    @Column(name = "COORDINATE_X", precision = 15, scale = 7)
    private BigDecimal coordinateX;

    @Column(name = "COORDINATE_Y", precision = 15, scale = 7)
    private BigDecimal coordinateY;

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 100)
    private String hygieneBusinessType;

    @Column(name = "MALE_EMPLOYEES")
    private Integer maleEmployees;

    @Column(name = "FEMALE_EMPLOYEES")
    private Integer femaleEmployees;

    @Column(name = "SURROUNDING_AREA", length = 200)
    private String surroundingArea;

    @Column(name = "GRADE_CATEGORY", length = 100)
    private String gradeCategory;

    @Column(name = "WATER_SUPPLY_TYPE", length = 100)
    private String waterSupplyType;

    @Column(name = "TOTAL_EMPLOYEES")
    private Integer totalEmployees;

    @Column(name = "HEAD_OFFICE_EMPLOYEES")
    private Integer headOfficeEmployees;

    @Column(name = "FACTORY_OFFICE_EMPLOYEES")
    private Integer factoryOfficeEmployees;

    @Column(name = "FACTORY_SALES_EMPLOYEES")
    private Integer factorySalesEmployees;

    @Column(name = "FACTORY_PRODUCTION_EMPLOYEES")
    private Integer factoryProductionEmployees;

    @Column(name = "BUILDING_OWNERSHIP", length = 100)
    private String buildingOwnership;

    @Column(name = "GUARANTEE_AMOUNT", precision = 15)
    private BigDecimal guaranteeAmount;

    @Column(name = "MONTHLY_RENT", precision = 15)
    private BigDecimal monthlyRent;

    @Column(name = "MULTI_USE_FACILITY", length = 20)
    private String multiUseFacility;

    @Column(name = "TOTAL_FACILITY_SIZE", precision = 10, scale = 2)
    private BigDecimal totalFacilitySize;

    @Column(name = "TRADITIONAL_DESIGNATION_NUMBER", length = 100)
    private String traditionalDesignationNumber;

    @Column(name = "TRADITIONAL_MAIN_FOOD", length = 500)
    private String traditionalMainFood;

    @Column(name = "HOMEPAGE", length = 1000)
    private String homepage;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}