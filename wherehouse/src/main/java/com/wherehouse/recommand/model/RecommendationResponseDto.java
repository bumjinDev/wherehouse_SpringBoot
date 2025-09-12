package com.wherehouse.recommand.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponseDto {

    @JsonProperty("searchStatus")
    private String searchStatus;

    @JsonProperty("message")
    private String message;

    @JsonProperty("recommendedDistricts")
    private List<RecommendedDistrictDto> recommendedDistricts;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RecommendedDistrictDto {

    @JsonProperty("rank")
    private Integer rank;

    @JsonProperty("districtName")
    private String districtName;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("topProperties")
    private List<TopPropertyDto> topProperties;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TopPropertyDto {

    @JsonProperty("propertyId")
    private Long propertyId;

    @JsonProperty("propertyName")
    private String propertyName;

    @JsonProperty("address")
    private String address;

    @JsonProperty("price")
    private Integer price;

    @JsonProperty("leaseType")
    private String leaseType;

    @JsonProperty("area")
    private Double area;

    @JsonProperty("floor")
    private Integer floor;

    @JsonProperty("buildYear")
    private Integer buildYear;

    @JsonProperty("finalScore")
    private Double finalScore;
}