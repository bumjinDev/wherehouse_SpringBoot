package com.wherehouse.information.model.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 주소 정보 DTO
 * 좌표를 카카오맵 API로 변환한 주소 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {

    @JsonProperty("road_address")
    private String roadAddress;  // 도로명 주소 (예: "서울특별시 중구 세종대로 110")

    @JsonProperty("jibun_address")
    private String jibunAddress;  // 지번 주소 (예: "서울특별시 중구 태평로1가 31")
}