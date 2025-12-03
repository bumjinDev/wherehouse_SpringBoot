package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 리뷰 목록 조회 API 응답 DTO
 * 
 * API 명세: 6.3 리뷰 목록 조회 API
 * Endpoint: GET /api/v1/reviews
 * Response Code: 200 OK
 * 
 * 응답 구조:
 * - 특정 매물 조회 시: filterMeta + reviews
 * - 전체 매물 조회 시: reviews만 포함
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewListResponseDto {
    
    /**
     * 필터링 메타 정보
     * 
     * - 특정 매물 조회 시(propertyId 지정)에만 포함
     * - 전체 리뷰 조회 시 null
     * - 조회 대상 매물의 ID와 평균 평점 포함
     */
    private FilterMetaDto filterMeta;
    
    /**
     * 리뷰 데이터 리스트
     * 
     * - 요청 조건에 맞는 리뷰 목록 (요약본)
     * - 각 리뷰는 ReviewSummaryDto 타입
     * - 빈 리스트일 수 있음
     */
    private List<ReviewSummaryDto> reviews;
}
