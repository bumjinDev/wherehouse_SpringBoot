package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySearchResultDto {

    /**
     * 매물 고유 식별자 (MD5 Hash)
     * - 프론트엔드에서 hidden value로 저장할 값
     */
    private String propertyId;

    /**
     * 매물 이름 (표시용)
     * - 예: "삼성아파트 (전세)"
     */
    private String propertyName;

    /**
     * 매물 타입
     * - 전세 / 월세
     */
    private String propertyType;
}