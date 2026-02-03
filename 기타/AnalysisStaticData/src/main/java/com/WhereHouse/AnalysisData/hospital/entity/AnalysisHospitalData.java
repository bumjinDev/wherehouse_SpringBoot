package com.WhereHouse.AnalysisData.hospital.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ANALYSIS_HOSPITAL_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisHospitalData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_hospital_data_seq")
    @SequenceGenerator(name = "analysis_hospital_data_seq", sequenceName = "SEQ_ANALYSIS_HOSPITAL_DATA", allocationSize = 1)
    private Long id;

    @Column(name = "BUSINESS_NAME", length = 4000)
    private String businessName;

    @Column(name = "BUSINESS_TYPE_NAME", length = 4000)
    private String businessTypeName;

    @Column(name = "DETAILED_STATUS_NAME", length = 4000)
    private String detailedStatusName;

    @Column(name = "PHONE_NUMBER", length = 4000)
    private String phoneNumber;

    @Column(name = "LOT_ADDRESS", length = 4000)
    private String lotAddress;

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;

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