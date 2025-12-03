package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 리뷰 요약 정보 DTO
 * 
 * 용도: 리뷰 목록 조회 시 각 리뷰의 요약 정보 전달
 * 부모 DTO: ReviewListResponseDto
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummaryDto {
    
    /**
     * 리뷰 ID
     * 
     * - 리뷰 상세 조회의 Key로 사용
     * - Long 타입
     */
    private Long reviewId;
    
    /**
     * 매물 식별자 (MD5 Hash)
     * 
     * - 32자 MD5 Hash
     * - String 타입
     */
    private String propertyId;
    
    /**
     * 아파트/매물명
     * 
     * - 리뷰가 작성된 매물의 이름
     */
    private String propertyName;
    
    /**
     * 작성자 ID (마스킹 처리)
     * 
     * - 예시: "user****"
     * - 개인정보 보호를 위한 마스킹 처리
     */
    private String userId;
    
    /**
     * 별점
     * 
     * - 1~5 사이의 정수값
     */
    private Integer rating;
}
