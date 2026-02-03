package com.WhereHouse.AnalysisStaticData.Lodgment.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "LODGING_BUSINESS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LodgingBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lodging_seq")
    @SequenceGenerator(name = "lodging_seq", sequenceName = "SEQ_LODGING_BUSINESS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERVICE_NAME", length = 100)
    private String serviceName = "데이터없음";

    @Column(name = "SERVICE_ID", length = 100)
    private String serviceId = "데이터없음";

    @Column(name = "LOCAL_GOV_CODE", length = 50)
    private String localGovCode = "데이터없음";

    @Column(name = "MANAGEMENT_NUMBER", length = 100)
    private String managementNumber = "데이터없음";

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;

    @Column(name = "BUSINESS_STATUS_CODE", length = 20)
    private String businessStatusCode = "데이터없음";

    @Column(name = "BUSINESS_STATUS_NAME", length = 100)
    private String businessStatusName = "데이터없음";

    @Column(name = "DETAIL_STATUS_CODE", length = 20)
    private String detailStatusCode = "데이터없음";

    @Column(name = "DETAIL_STATUS_NAME", length = 100)
    private String detailStatusName = "데이터없음";

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;

    @Column(name = "REOPENING_DATE")
    private LocalDate reopeningDate;

    @Column(name = "LOCATION_PHONE", length = 200)  // 100 → 200으로 증가
    private String locationPhone = "데이터없음";

    @Column(name = "LOCATION_AREA", precision = 15, scale = 5)  // precision 증가
    private BigDecimal locationArea = BigDecimal.ZERO;

    @Column(name = "LOCATION_POSTAL_CODE", length = 20)
    private String locationPostalCode = "데이터없음";

    @Column(name = "FULL_ADDRESS", length = 2000)  // 1000 → 2000으로 증가
    private String fullAddress = "데이터없음";

    @Column(name = "ROAD_ADDRESS", length = 2000)  // 1000 → 2000으로 증가
    private String roadAddress = "데이터없음";

    @Column(name = "ROAD_POSTAL_CODE", length = 20)
    private String roadPostalCode = "데이터없음";

    @Column(name = "BUSINESS_NAME", length = 1000)  // 500 → 1000으로 증가
    private String businessName = "데이터없음";

    @Column(name = "LAST_UPDATE_TIME")
    private LocalDateTime lastUpdateTime;

    @Column(name = "DATA_UPDATE_TYPE", length = 20)
    private String dataUpdateType = "데이터없음";

    @Column(name = "DATA_UPDATE_DATE")
    private LocalDateTime dataUpdateDate;

    @Column(name = "BUSINESS_TYPE_NAME", length = 200)
    private String businessTypeName = "데이터없음";

    @Column(name = "COORD_X", precision = 25, scale = 10)  // 15 → 25로 증가
    private BigDecimal coordX = BigDecimal.ZERO;

    @Column(name = "COORD_Y", precision = 25, scale = 10)  // 15 → 25로 증가
    private BigDecimal coordY = BigDecimal.ZERO;

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 200)
    private String hygieneBusinessType = "데이터없음";

    @Column(name = "BUILDING_GROUND_FLOORS")
    private Integer buildingGroundFloors = 0;

    @Column(name = "BUILDING_BASEMENT_FLOORS")
    private Integer buildingBasementFloors = 0;

    @Column(name = "USE_START_GROUND_FLOOR")
    private Integer useStartGroundFloor = 0;

    @Column(name = "USE_END_GROUND_FLOOR")
    private Integer useEndGroundFloor = 0;

    @Column(name = "USE_START_BASEMENT_FLOOR")
    private Integer useStartBasementFloor = 0;

    @Column(name = "USE_END_BASEMENT_FLOOR")
    private Integer useEndBasementFloor = 0;

    @Column(name = "KOREAN_ROOM_COUNT")
    private Integer koreanRoomCount = 0;

    @Column(name = "WESTERN_ROOM_COUNT")
    private Integer westernRoomCount = 0;

    @Column(name = "CONDITIONAL_PERMIT_REASON", length = 2000)  // 1000 → 2000으로 증가
    private String conditionalPermitReason = "데이터없음";

    @Column(name = "CONDITIONAL_PERMIT_START")
    private LocalDate conditionalPermitStart;

    @Column(name = "CONDITIONAL_PERMIT_END")
    private LocalDate conditionalPermitEnd;

    @Column(name = "BUILDING_OWNERSHIP_TYPE", length = 100)
    private String buildingOwnershipType = "데이터없음";

    @Column(name = "FEMALE_EMPLOYEE_COUNT")
    private Integer femaleEmployeeCount = 0;

    @Column(name = "MALE_EMPLOYEE_COUNT")
    private Integer maleEmployeeCount = 0;

    @Column(name = "MULTI_USE_FACILITY_YN", length = 1)
    private String multiUseFacilityYn = "N";

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastUpdateTime == null) {
            lastUpdateTime = LocalDateTime.now();
        }
        if (dataUpdateDate == null) {
            dataUpdateDate = LocalDateTime.now();
        }

        // NULL 값들을 기본값으로 설정
        if (serviceName == null) serviceName = "데이터없음";
        if (serviceId == null) serviceId = "데이터없음";
        if (localGovCode == null) localGovCode = "데이터없음";
        if (managementNumber == null) managementNumber = "데이터없음";
        if (businessStatusCode == null) businessStatusCode = "데이터없음";
        if (businessStatusName == null) businessStatusName = "데이터없음";
        if (detailStatusCode == null) detailStatusCode = "데이터없음";
        if (detailStatusName == null) detailStatusName = "데이터없음";
        if (locationPhone == null) locationPhone = "데이터없음";
        if (locationPostalCode == null) locationPostalCode = "데이터없음";
        if (fullAddress == null) fullAddress = "데이터없음";
        if (roadAddress == null) roadAddress = "데이터없음";
        if (roadPostalCode == null) roadPostalCode = "데이터없음";
        if (businessName == null) businessName = "데이터없음";
        if (dataUpdateType == null) dataUpdateType = "데이터없음";
        if (businessTypeName == null) businessTypeName = "데이터없음";
        if (hygieneBusinessType == null) hygieneBusinessType = "데이터없음";
        if (conditionalPermitReason == null) conditionalPermitReason = "데이터없음";
        if (buildingOwnershipType == null) buildingOwnershipType = "데이터없음";
    }
}