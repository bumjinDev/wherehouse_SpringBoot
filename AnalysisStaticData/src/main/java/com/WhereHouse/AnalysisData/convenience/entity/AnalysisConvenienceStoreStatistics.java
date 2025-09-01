package com.wherehouse.AnalysisData.convenience.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_CONVENIENCE_STORE_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisConvenienceStoreStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_convenience_seq")
    @SequenceGenerator(name = "analysis_convenience_seq", sequenceName = "SEQ_ANALYSIS_CONVENIENCE_STORE_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "KAKAO_PLACE_ID")
    private String kakaoPlaceId;

    @Column(name = "PLACE_NAME")
    private String placeName;

    @Column(name = "CATEGORY_NAME")
    private String categoryName;

    @Column(name = "CATEGORY_GROUP_CODE")
    private String categoryGroupCode;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "ADDRESS_NAME")
    private String addressName;

    @Column(name = "ROAD_ADDRESS_NAME")
    private String roadAddressName;

    @Column(name = "LONGITUDE")
    private BigDecimal longitude;

    @Column(name = "LATITUDE")
    private BigDecimal latitude;

    @Column(name = "PLACE_URL")
    private String placeUrl;

    @Column(name = "DISTRICT")
    private String district;

    @Column(name = "STORE_BRAND")
    private String storeBrand;
}