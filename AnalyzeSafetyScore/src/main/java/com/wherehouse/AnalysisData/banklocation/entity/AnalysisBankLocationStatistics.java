package com.wherehouse.AnalysisData.banklocation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ANALYSIS_BANK_LOCATION_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisBankLocationStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_bank_location_seq")
    @SequenceGenerator(name = "analysis_bank_location_seq", sequenceName = "SEQ_ANALYSIS_BANK_LOCATION_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "KAKAO_PLACE_ID", length = 50)
    private String kakaoPlaceId;

    @Column(name = "PLACE_NAME", length = 200)
    private String placeName;

    @Column(name = "CATEGORY_NAME", length = 100)
    private String categoryName;

    @Column(name = "CATEGORY_GROUP_CODE", length = 10)
    private String categoryGroupCode;

    @Column(name = "ADDRESS_NAME", length = 300)
    private String addressName;

    @Column(name = "ROAD_ADDRESS_NAME", length = 300)
    private String roadAddressName;

    @Column(name = "LONGITUDE", precision = 10, scale = 8)
    private BigDecimal longitude;

    @Column(name = "LATITUDE", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "PLACE_URL", length = 500)
    private String placeUrl;

    @Column(name = "DISTRICT", length = 50)
    private String district;

    @Column(name = "BANK_BRAND", length = 50)
    private String bankBrand;
}