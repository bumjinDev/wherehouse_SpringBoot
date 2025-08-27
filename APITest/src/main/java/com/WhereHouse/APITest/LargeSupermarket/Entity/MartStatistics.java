package com.WhereHouse.APITest.LargeSupermarket.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "MART_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MartStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mart_seq")
    @SequenceGenerator(name = "mart_seq", sequenceName = "SEQ_MART_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "LOCAL_GOVT_CODE", nullable = false, length = 20)
    private String localGovtCode;

    @Column(name = "MANAGEMENT_NO", nullable = false, length = 30)
    private String managementNo;

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 10)
    private String businessStatusCode;

    @Column(name = "BUSINESS_STATUS_NAME", length = 30)
    private String businessStatusName;

    @Column(name = "DETAIL_STATUS_CODE", length = 10)
    private String detailStatusCode;

    @Column(name = "DETAIL_STATUS_NAME", length = 30)
    private String detailStatusName;

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPEN_DATE")
    private LocalDate reopenDate;

    @Column(name = "PHONE_NUMBER", length = 30)
    private String phoneNumber;

    @Column(name = "LOCATION_AREA")
    private Double locationArea;

    @Column(name = "LOCATION_POSTAL_CODE", length = 10)
    private String locationPostalCode;

    @Column(name = "ADDRESS", length = 300)
    private String address;

    @Column(name = "ROAD_ADDRESS", length = 300)
    private String roadAddress;

    @Column(name = "ROAD_POSTAL_CODE", length = 10)
    private String roadPostalCode;

    @Column(name = "BUSINESS_NAME", length = 100)
    private String businessName;

    @Column(name = "LAST_UPDATE_DATE")
    private LocalDate lastUpdateDate;

    @Column(name = "DATA_UPDATE_TYPE", length = 10)
    private String dataUpdateType;

    @Column(name = "DATA_UPDATE_DATE", length = 20)
    private String dataUpdateDate;

    @Column(name = "BUSINESS_TYPE_NAME", length = 50)
    private String businessTypeName;

    @Column(name = "COORD_X")
    private Double coordX;

    @Column(name = "COORD_Y")
    private Double coordY;

    @Column(name = "STORE_TYPE_NAME", length = 50)
    private String storeTypeName;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}