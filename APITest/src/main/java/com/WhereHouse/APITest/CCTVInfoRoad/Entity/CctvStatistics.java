package com.WhereHouse.API.Test.APITest.CCTVInfoRoad.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "CCTV_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CctvStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cctv_seq")
    @SequenceGenerator(name = "cctv_seq", sequenceName = "SEQ_CCTV_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "MANAGEMENT_AGENCY", length = 100)
    private String managementAgency = "데이터없음";

    @Column(name = "ROAD_ADDRESS", length = 200)
    private String roadAddress = "데이터없음";

    @Column(name = "JIBUN_ADDRESS", length = 200)
    private String jibunAddress = "데이터없음";

    @Column(name = "INSTALL_PURPOSE", length = 50)
    private String installPurpose = "데이터없음";

    @Column(name = "CAMERA_COUNT")
    private Integer cameraCount = 0;

    @Column(name = "CAMERA_PIXEL")
    private Integer cameraPixel = 0;

    @Column(name = "SHOOTING_DIRECTION", length = 100)
    private String shootingDirection = "데이터없음";

    @Column(name = "STORAGE_DAYS")
    private Integer storageDays = 0;

    @Column(name = "INSTALL_DATE", length = 20)
    private String installDate = "데이터없음";

    @Column(name = "MANAGEMENT_PHONE", length = 20)
    private String managementPhone = "데이터없음";

    @Column(name = "WGS84_LATITUDE")
    private Double wgs84Latitude = 0.0;

    @Column(name = "WGS84_LONGITUDE")
    private Double wgs84Longitude = 0.0;

    @Column(name = "DATA_BASE_DATE", length = 20)
    private String dataBaseDate = "데이터없음";

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}