package com.wherehouse.rest.redius.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "MAPDATA")
@Getter
@Setter
public class MapDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "GUID", nullable = false)
    private Double guid;	// 필수 데이터는 아니지만 js 에서 디버깅 용으로 사용.

    @Column(name = "GUNAME", nullable = false)
    private String guname;

    @Column(name = "LATITUDE", nullable = false)
    private Double latitude;

    @Column(name = "LONGITUDE", nullable = false)
    private Double longitude;
}
