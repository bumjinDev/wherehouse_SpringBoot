package com.WhereHouse.APITest.FinancialInstitutionDetail.Config;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
public class SeoulDistrictCoords {
    private String name;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public static final List<SeoulDistrictCoords> SEOUL_DISTRICTS = Arrays.asList(
            new SeoulDistrictCoords("강남구", new BigDecimal("37.5173"), new BigDecimal("127.0473")),
            new SeoulDistrictCoords("강동구", new BigDecimal("37.5301"), new BigDecimal("127.1238")),
            new SeoulDistrictCoords("강북구", new BigDecimal("37.6369"), new BigDecimal("127.0254")),
            new SeoulDistrictCoords("강서구", new BigDecimal("37.5509"), new BigDecimal("126.8495")),
            new SeoulDistrictCoords("관악구", new BigDecimal("37.4781"), new BigDecimal("126.9515")),
            new SeoulDistrictCoords("광진구", new BigDecimal("37.5384"), new BigDecimal("127.0822")),
            new SeoulDistrictCoords("구로구", new BigDecimal("37.4954"), new BigDecimal("126.8874")),
            new SeoulDistrictCoords("금천구", new BigDecimal("37.4569"), new BigDecimal("126.8896")),
            new SeoulDistrictCoords("노원구", new BigDecimal("37.6542"), new BigDecimal("127.0568")),
            new SeoulDistrictCoords("도봉구", new BigDecimal("37.6688"), new BigDecimal("127.0471")),
            new SeoulDistrictCoords("동대문구", new BigDecimal("37.5744"), new BigDecimal("127.0396")),
            new SeoulDistrictCoords("동작구", new BigDecimal("37.5124"), new BigDecimal("126.9393")),
            new SeoulDistrictCoords("마포구", new BigDecimal("37.5663"), new BigDecimal("126.9019")),
            new SeoulDistrictCoords("서대문구", new BigDecimal("37.5794"), new BigDecimal("126.9368")),
            new SeoulDistrictCoords("서초구", new BigDecimal("37.4837"), new BigDecimal("127.0324")),
            new SeoulDistrictCoords("성동구", new BigDecimal("37.5635"), new BigDecimal("127.0370")),
            new SeoulDistrictCoords("성북구", new BigDecimal("37.5894"), new BigDecimal("127.0167")),
            new SeoulDistrictCoords("송파구", new BigDecimal("37.5145"), new BigDecimal("127.1059")),
            new SeoulDistrictCoords("양천구", new BigDecimal("37.5168"), new BigDecimal("126.8664")),
            new SeoulDistrictCoords("영등포구", new BigDecimal("37.5264"), new BigDecimal("126.8962")),
            new SeoulDistrictCoords("용산구", new BigDecimal("37.5326"), new BigDecimal("126.9906")),
            new SeoulDistrictCoords("은평구", new BigDecimal("37.6026"), new BigDecimal("126.9292")),
            new SeoulDistrictCoords("종로구", new BigDecimal("37.5735"), new BigDecimal("126.9788")),
            new SeoulDistrictCoords("중구", new BigDecimal("37.5640"), new BigDecimal("126.9979")),
            new SeoulDistrictCoords("중랑구", new BigDecimal("37.6063"), new BigDecimal("127.0926"))
    );
}