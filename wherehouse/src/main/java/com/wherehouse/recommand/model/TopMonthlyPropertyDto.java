package com.wherehouse.recommand.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 월세 추천 매물 정보 DTO (Lv 3. Item)
 *
 * 역할:
 * 1. 추천된 지역구(`RecommendedMonthlyDistrictDto`) 내에 포함될 상위 매물(Top-N)의 요약 정보를 담습니다.
 * 2. 리스트 화면에서 사용자에게 보여줄 핵심 정보(보증금, 월세, 평점 등)를 제공합니다.
 *
 * 데이터 출처 (Phase 2):
 * - 매물 기본 정보: Redis (`property:monthly:{id}`)
 * - 리뷰 통계 정보: Oracle RDB (`REVIEW_STATISTICS` 테이블 조회) -> Service에서 주입
 * - 최종 점수: Service 계층에서 하이브리드 로직((정량+정성)/2)으로 산출된 값
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopMonthlyPropertyDto {

    /**
     * 매물 고유 식별자 (MD5 Hash)
     * - [변경] 기존 Long -> String (32자 Hex String)
     * - 용도: 프론트엔드에서 상세 조회 API(/api/properties/{id}) 호출 시 Key로 사용
     */
    private String propertyId;

    /**
     * 아파트/건물명
     */
    private String propertyName;

    /**
     * 전체 주소
     */
    private String address;

    /**
     * 보증금 (단위: 만원)
     * - 월세 계약의 보증금액
     */
    private Integer price;

    /**
     * 월세금 (단위: 만원)
     * - 매달 납부해야 하는 임대료
     */
    private Integer monthlyRent;

    /**
     * 임대 유형
     * - 고정값: "월세"
     */
    private String leaseType;

    /**
     * 전용 면적 (평)
     */
    private Double area;

    /**
     * 층수
     */
    private Integer floor;

    /**
     * 건축연도
     */
    private Integer buildYear;

    /**
     * 최종 추천 점수 (Hybrid Score)
     * - 범위: 0 ~ 100점
     * - 구성: (공공데이터 정량점수 * 0.5) + (리뷰 정성점수 * 0.5)
     * (월세의 경우 정량점수 계산 시 보증금 점수와 월세금 점수를 평균내어 사용)
     */
    private Double finalScore;

    // ======================================================================
    // [Phase 2 신규 추가 필드] 리뷰 시스템 연동 데이터
    // ======================================================================

    /**
     * 리뷰 개수
     * - 용도: 데이터 신뢰도 지표
     * - 데이터 소스: RDB REVIEWS 테이블 집계
     */
    private Integer reviewCount;

    /**
     * 평균 별점
     * - 용도: 사용자 만족도 지표 (0.0 ~ 5.0)
     * - 데이터 소스: RDB REVIEWS 테이블 집계
     */
    private Double avgRating;
}