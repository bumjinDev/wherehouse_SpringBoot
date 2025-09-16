package com.wherehouse.recommand.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * API 응답용 추천 지역구 정보 DTO
 * 지역구별 순위, 이름, 추천 요약, 대표 매물 목록을 포함
 */
@Builder
@Getter
@ToString
public class RecommendedDistrictDto {

    /**
     * 지역구 추천 순위 (1위, 2위, 3위)
     */
    private Integer rank;

    /**
     * 지역구 이름 (예: "강남구", "서초구")
     */
    private String districtName;

    /**
     * 이 지역구를 추천하는 핵심 요약 근거
     * 예: "가격 1순위 조건에 가장 부합하며, 조건 내 추천 매물이 12건 있습니다."
     */
    private String summary;

    /**
     * 해당 지역구 내에서 가장 점수가 높은 대표 매물 목록 (최대 3개)
     */
    private List<TopPropertyDto> topProperties;
}