package com.wherehouse.recommand.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * API 응답용 대표 매물 정보 DTO
 * 지역구별 최상위 점수 매물들의 핵심 정보를 포함
 */
@Builder
@Getter
@ToString
public class TopPropertyDto {

    /**
     * 매물 고유 ID (UUID 문자열)
     */
    private String propertyId;

    /**
     * 아파트/오피스텔 이름
     */
    private String propertyName;

    /**
     * 법정동과 지번을 조합한 주소
     */
    private String address;

    /**
     * 보증금 또는 전세금 (단위: 만원)
     */
    private Integer price;

    /**
     * 임대 유형 ('전세' 또는 '월세')
     */
    private String leaseType;

    /**
     * 전용 면적 (단위: 평)
     * (전용면적㎡ ÷ 3.3058)로 계산
     */
    private Double area;

    /**
     * 층수
     */
    private Integer floor;

    /**
     * 건축 연도
     */
    private Integer buildYear;

    /**
     * 매물의 최종 추천 점수 (0~100점)
     * 사용자 우선순위에 따른 가중치가 적용된 점수
     */
    private Double finalScore;
}