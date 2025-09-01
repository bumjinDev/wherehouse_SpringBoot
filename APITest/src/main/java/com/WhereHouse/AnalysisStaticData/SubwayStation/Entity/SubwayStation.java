package com.WhereHouse.APITest.SubwayStation.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "SUBWAY_STATION")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubwayStation {

    @Id
    @Column(name = "STATION_CODE", length = 10)
    private String stationCode;

    @Column(name = "STATION_NAME_KOR", length = 100, nullable = false)
    private String stationNameKor;

    @Column(name = "STATION_NAME_ENG", length = 200)
    private String stationNameEng;

    @Column(name = "LINE_NUMBER", length = 10)
    private String lineNumber;

    @Column(name = "EXTERNAL_CODE", length = 20)
    private String externalCode;

    @Column(name = "STATION_NAME_CHN", length = 100)
    private String stationNameChn;

    @Column(name = "STATION_NAME_JPN", length = 100)
    private String stationNameJpn;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();

        // NULL 값들을 기본값으로 설정
        if (stationNameEng == null) stationNameEng = "데이터없음";
        if (lineNumber == null) lineNumber = "데이터없음";
        if (externalCode == null) externalCode = "데이터없음";
        if (stationNameChn == null) stationNameChn = "数据不存在";
        if (stationNameJpn == null) stationNameJpn = "データなし";
    }
}