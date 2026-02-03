package com.WhereHouse.AnalysisStaticData.SubwayStation.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "SUBWAY_STATION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubwayStation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_subway_station")
    @SequenceGenerator(name = "seq_subway_station", sequenceName = "SEQ_SUBWAY_STATION_ID", allocationSize = 1)
    @Column(name = "STATION_ID")
    private Long stationId;

    @NotNull
    @Column(name = "SEQ_NO", nullable = false)
    private Integer seqNo;

    @NotBlank
    @Column(name = "STATION_NUMBER", length = 200, nullable = false)
    private String stationNumber;

    @NotBlank
    @Column(name = "LINE_NUMBER", length = 200, nullable = false)
    private String lineNumber;

    @NotBlank
    @Column(name = "STATION_NAME_KOR", length = 100, nullable = false)
    private String stationNameKor;

    @Column(name = "STATION_PHONE", length = 200)
    private String stationPhone;

    @Column(name = "ROAD_ADDRESS", length = 500)
    private String roadAddress;

    @Column(name = "JIBUN_ADDRESS", length = 500)
    private String jibunAddress;

    @PrePersist
    protected void onCreate() {
        // 필요시 추가 로직
    }
}