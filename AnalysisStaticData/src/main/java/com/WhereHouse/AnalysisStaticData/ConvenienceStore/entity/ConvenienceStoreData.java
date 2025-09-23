package com.WhereHouse.AnalysisStaticData.ConvenienceStore.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "CONVENIENCE_STORE_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvenienceStoreData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "convenience_seq")
    @SequenceGenerator(name = "convenience_seq", sequenceName = "SEQ_CONVENIENCE_STORE_DATA", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "OPEN_LOCAL_GOV_CODE", length = 50)
    private String openLocalGovCode;                    // 개방자치단체코드

    @Column(name = "MANAGEMENT_NUMBER", length = 50)
    private String managementNumber;                    // 관리번호

    @Column(name = "LICENSE_DATE")
    private LocalDate licenseDate;                      // 인허가일자

    @Column(name = "LICENSE_CANCEL_DATE")
    private LocalDate licenseCancelDate;                // 인허가취소일자

    @Column(name = "BUSINESS_STATUS_CODE", length = 50)
    private String businessStatusCode;                  // 영업상태코드

    @Column(name = "BUSINESS_STATUS_NAME", length = 50)
    private String businessStatusName;                  // 영업상태명

    @Column(name = "DETAILED_STATUS_CODE", length = 50)
    private String detailedStatusCode;                  // 상세영업상태코드

    @Column(name = "DETAILED_STATUS_NAME", length = 50)
    private String detailedStatusName;                  // 상세영업상태명

    @Column(name = "CLOSURE_DATE")
    private LocalDate closureDate;                      // 폐업일자

    @Column(name = "SUSPENSION_START_DATE")
    private LocalDate suspensionStartDate;              // 휴업시작일자

    @Column(name = "SUSPENSION_END_DATE")
    private LocalDate suspensionEndDate;                // 휴업종료일자

    @Column(name = "REOPENING_DATE")
    private LocalDate reopeningDate;                    // 재개업일자

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;                         // 전화번호

    @Column(name = "LOCATION_AREA", precision = 15, scale = 2)
    private BigDecimal locationArea;                    // 소재지면적

    @Column(name = "LOCATION_POSTAL_CODE", length = 50)
    private String locationPostalCode;                  // 소재지우편번호

    @Column(name = "LOT_ADDRESS", length = 500)
    private String lotAddress;                          // 지번주소

    @Column(name = "ROAD_ADDRESS", length = 500)
    private String roadAddress;                         // 도로명주소

    @Column(name = "ROAD_POSTAL_CODE", length = 50)
    private String roadPostalCode;                      // 도로명우편번호

    @Column(name = "BUSINESS_NAME", length = 200)
    private String businessName;                        // 사업장명

    @Column(name = "LAST_MODIFIED_DATE")
    private LocalDateTime lastModifiedDate;             // 최종수정일자

    @Column(name = "DATA_UPDATE_TYPE", length = 50)
    private String dataUpdateType;                      // 데이터갱신구분

    @Column(name = "DATA_UPDATE_TIME", length = 50)
    private String dataUpdateTime;                      // 데이터갱신일자

    @Column(name = "BUSINESS_TYPE_NAME", length = 100)
    private String businessTypeName;                    // 업태구분명

    @Column(name = "COORDINATE_X", precision = 15, scale = 10)
    private BigDecimal coordinateX;                     // 좌표정보(X)

    @Column(name = "COORDINATE_Y", precision = 20, scale = 10)
    private BigDecimal coordinateY;                     // 좌표정보(Y)

    @Column(name = "SALES_AREA", precision = 20, scale = 2)
    private BigDecimal salesArea;                       // 판매점영업면적

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;                    // 데이터 로드 시각

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;                    // 데이터 수정 시각

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