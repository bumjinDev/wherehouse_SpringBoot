package com.wherehouse.recommand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전세 추천 매물 정보 DTO (Lv 3. Item)
 *
 * 역할:
 * 1. 추천된 지역구(`RecommendedCharterDistrictDto`) 내에 포함될 상위 매물(Top-N)의 요약 정보를 담습니다.
 * 2. 리스트 화면에서 사용자에게 보여줄 핵심 정보(가격, 평수, 평점 등)를 제공합니다.
 * 3. 클릭 시 상세 페이지로 이동하기 위한 식별자(`propertyId`)를 포함합니다.
 *
 * 데이터 출처 (Phase 2):
 * - 매물 기본 정보: Redis (`property:charter:{id}`)
 * - 리뷰 통계 정보: Oracle RDB (`REVIEW_STATISTICS` 테이블 조회) -> Service에서 주입
 * - 최종 점수: Service 계층에서 하이브리드 로직((정량+정성)/2)으로 산출된 값
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopCharterPropertyDto {

    /**
     * 매물 고유 식별자 (MD5 Hash)
     * - [변경] 기존 Long -> String (32자 Hex String)
     * - 용도: 프론트엔드에서 상세 조회 API(/api/properties/{id}) 호출 시 Key로 사용
     */
    private String propertyId;

    /**
     * 아파트/건물명
     * - 예: "래미안 그레이튼 2차"
     */
    private String propertyName;

    /**
     * 전체 주소
     * - 예: "서울시 강남구 역삼동 763-16"
     */
    private String address;

    /**
     * 전세금 (단위: 만원)
     * - 예: 50000 -> 5억원
     */
    private Integer price;

    /**
     * 임대 유형
     * - 고정값: "전세"
     */
    private String leaseType;

    /**
     * 전용 면적 (평)
     * - 소수점 포함 (예: 25.4)
     */
    private Double area;

    /**
     * 층수
     */
    private Integer floor;

    /**
     * 건축연도
     * - 예: 2015
     */
    private Integer buildYear;

    /**
     * 최종 추천 점수 (Hybrid Score)
     * - 범위: 0 ~ 100점
     * - 구성: (공공데이터 정량점수 * 0.5) + (리뷰 정성점수 * 0.5)
     * - 정렬 기준: 이 점수가 높은 순으로 리스트에 나열됨
     */
    private Double finalScore;

    // ======================================================================
    // [Phase 2 신규 추가 필드] 리뷰 시스템 연동 데이터
    // ======================================================================

    /**
     * 리뷰 개수
     * - 용도: 사용자에게 데이터의 신뢰도를 보여주는 척도 ("리뷰 12개")
     * - 데이터 소스: RDB REVIEWS 테이블 집계
     */
    private Integer reviewCount;

    /**
     * 평균 별점
     * - 용도: 사용자 만족도 표시 (예: 4.5점)
     * - 범위: 0.0 ~ 5.0
     * - 데이터 소스: RDB REVIEWS 테이블 집계
     */
    private Double avgRating;
}