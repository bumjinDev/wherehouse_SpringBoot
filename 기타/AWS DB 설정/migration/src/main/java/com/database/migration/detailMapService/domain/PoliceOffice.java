package com.database.migration.detailMapService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "POLICEOFFICE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliceOffice {

    @Id
    @Column(name = "ADDRESS")
    private String address;

    @Column(name = "LATITUDE")
    private java.math.BigDecimal latitude;

    @Column(name = "LONGITUDE")
    private java.math.BigDecimal longitude;
}