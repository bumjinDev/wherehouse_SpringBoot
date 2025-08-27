package com.WhereHouse.API.Test.APITest.StreetLightRaw.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "STREETLIGHT_RAW_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreetlightRawData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "streetlight_raw_seq")
    @SequenceGenerator(name = "streetlight_raw_seq", sequenceName = "SEQ_STREETLIGHT_RAW_DATA", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MANAGEMENT_NUMBER", nullable = false, length = 50)
    private String managementNumber;

    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude = BigDecimal.ZERO;

    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude = BigDecimal.ZERO;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}