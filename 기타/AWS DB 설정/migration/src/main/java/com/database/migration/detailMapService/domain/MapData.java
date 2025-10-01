package com.database.migration.detailMapService.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "MAPDATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapData {

    @Id
    @Column(name = "GUID")
    private Double guid;

    @Column(name = "LATITUDE")
    private Double latitude;

    @Column(name = "LONGITUDE")
    private Double longitude;

    @Column(name = "ID")
    private Long id;

    @Column(name = "GUNAME")
    private String guname;
}