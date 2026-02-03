package com.WhereHouse.AnalysisStaticData.HospitalSave.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "HOSPITAL_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hospital_data_seq")
    @SequenceGenerator(name = "hospital_data_seq", sequenceName = "SEQ_HOSPITAL_DATA", allocationSize = 1)
    private Long id;

    @Column(name = "OPEN_LOCAL_GOV_CODE", length = 50)
    private String openLocalGovCode;

    @Column(name = "MANAGEMENT_NUMBER", length = 50, nullable = false)
    private String managementNumber;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 50)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 50)
    private String businessStatusName;

    @Column(name = "DETAILED_STATUS_CODE", length = 50)
    private String detailedStatusCode;

    @Column(name = "DETAILED_STATUS_NAME", length = 50)
    private String detailedStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPENING_DATE")
    private LocalDate reopeningDate;

    @Column(name = "PHONE_NUMBER", length = 255)
    private String phoneNumber;

    @Column(name = "LOCATION_AREA", precision = 20, scale = 2)
    private BigDecimal locationArea;

    @Column(name = "LOCATION_POSTAL_CODE", length = 50)
    private String locationPostalCode;

    @Column(name = "LOT_ADDRESS", length = 500)
    private String lotAddress;

    @Column(name = "ROAD_ADDRESS", length = 500)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 50)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 200)
    private String businessName;

    @Column(name = "LAST_MODIFIED_DATE")
    private LocalDateTime lastModifiedDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 50)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_TIME", length = 50)
    private String dataUpdateTime;

    @Column(name = "BUSINESS_TYPE_NAME", length = 100)
    private String businessTypeName;

    @Column(name = "COORDINATE_X", precision = 20, scale = 10)
    private BigDecimal coordinateX;

    @Column(name = "COORDINATE_Y", precision = 20, scale = 10)
    private BigDecimal coordinateY;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}