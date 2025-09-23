package com.WhereHouse.AnalysisStaticData.Police.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "POLICE_FACILITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliceFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "police_facility_seq")
    @SequenceGenerator(name = "police_facility_seq", sequenceName = "SEQ_POLICE_FACILITY", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SERIAL_NO")
    private Integer serialNo;

    @Column(name = "CITY_PROVINCE", nullable = false, length = 50)
    private String cityProvince;

    @Column(name = "POLICE_STATION", nullable = false, length = 100)
    private String policeStation;

    @Column(name = "FACILITY_NAME", nullable = false, length = 100)
    private String facilityName;

    @Column(name = "FACILITY_TYPE", nullable = false, length = 50)
    private String facilityType;

    @Column(name = "PHONE_NUMBER", length = 50)
    private String phoneNumber;

    @Column(name = "ADDRESS", nullable = false, length = 500)
    private String address;

    @Column(name = "COORD_X")
    private Double coordX;

    @Column(name = "COORD_Y")
    private Double coordY;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}