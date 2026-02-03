package com.wherehouse.recommand.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDto {

    @JsonProperty("propertyId")
    private Long propertyId;

    @JsonProperty("propertyName")
    private String propertyName;     // 아파트명

    @JsonProperty("address")
    private String address;          // 전체 주소

    @JsonProperty("umdNm")
    private String umdNm;           // 법정동명

    @JsonProperty("jibun")
    private String jibun;           // 지번

    @JsonProperty("sggCd")
    private String sggCd;           // 시군구코드

    @JsonProperty("districtName")
    private String districtName;     // 지역구명

    @JsonProperty("deposit")
    private Integer deposit;         // 보증금/전세금

    @JsonProperty("monthlyRent")
    private Integer monthlyRent;     // 월세 (전세의 경우 0)

    @JsonProperty("leaseType")
    private String leaseType;        // CHARTER/MONTHLY

    @JsonProperty("excluUseAr")
    private Double excluUseAr;       // 전용면적(㎡)

    @JsonProperty("areaInPyeong")
    private Double areaInPyeong;     // 전용면적(평)

    @JsonProperty("floor")
    private Integer floor;           // 층수

    @JsonProperty("buildYear")
    private Integer buildYear;       // 건축연도

    @JsonProperty("dealDate")
    private String dealDate;         // 계약일자

    @JsonProperty("rgstDate")
    private String rgstDate;         // 등록일자

    @JsonProperty("safetyScore")
    private Double safetyScore;      // 안전성 점수

    @JsonProperty("finalScore")
    private Double finalScore;       // 최종 추천 점수

    // 가격 관련 편의 메서드
    public Integer getPrice() {
        return "CHARTER".equals(leaseType) ? deposit : deposit;
    }

    public String getDisplayLeaseType() {
        return "CHARTER".equals(leaseType) ? "전세" : "월세";
    }
}