package com.database.migration.detailMapService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CCTV")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cctv {

    @Id
    @Column(name = "NUMBERS")
    private Long numbers;

    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "LATITUDE")
    private java.math.BigDecimal latitude;

    @Column(name = "LONGITUDE")
    private java.math.BigDecimal longitude;

    @Column(name = "CAMERACOUNT")
    private Long cameraCount;
}