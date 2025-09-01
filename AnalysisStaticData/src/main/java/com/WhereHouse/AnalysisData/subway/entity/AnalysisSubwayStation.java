package com.wherehouse.AnalysisData.subway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_SUBWAY_STATION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSubwayStation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_subway_seq")
    @SequenceGenerator(name = "analysis_subway_seq", sequenceName = "SEQ_ANALYSIS_SUBWAY_STATION", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "STATION_NAME_KOR")
    private String stationNameKor;

    @Column(name = "STATION_PHONE")
    private String stationPhone;

    @Column(name = "ROAD_ADDRESS")
    private String roadAddress;

    @Column(name = "JIBUN_ADDRESS")
    private String jibunAddress;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;
}