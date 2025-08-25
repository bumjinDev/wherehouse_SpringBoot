package com.WhereHouse.API.Test.APITest.CommunityCenterRoad.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "RESIDENT_CENTER")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResidentCenter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "resident_center_seq")
    @SequenceGenerator(name = "resident_center_seq", sequenceName = "SEQ_RESIDENT_CENTER", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERIAL_NO", nullable = false)
    private Integer serialNo;

    @Column(name = "SIDO", nullable = false, length = 50)
    private String sido;

    @Column(name = "SIGUNGU", nullable = false, length = 100)
    private String sigungu;

    @Column(name = "EUPMEONDONG", nullable = false, length = 100)
    private String eupmeondong;

    @Column(name = "POSTAL_CODE", length = 10)
    private String postalCode;

    @Column(name = "ADDRESS", nullable = false, length = 500)
    private String address;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}