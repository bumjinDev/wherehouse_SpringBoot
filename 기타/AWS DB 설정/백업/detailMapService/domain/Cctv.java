package com.aws.database.detailMapService.domain;

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
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Column(name = "CAMERACOUNT")
    private Long cameraCount;
}