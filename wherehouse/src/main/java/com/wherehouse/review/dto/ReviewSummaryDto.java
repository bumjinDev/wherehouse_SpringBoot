package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 리뷰 요약 정보 DTO
 * * 용도: 리뷰 목록 조회 시 각 리뷰의 요약 정보 전달
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
     */
    private Long reviewId;

    /**
     * 매물 식별자 (MD5 Hash)
     */
    private String propertyId;

    /**
     * 아파트/매물명
     */
    private String propertyName;

    /**
     * 작성자 ID (마스킹 처리됨)
     */
    private String userId;

    /**
     * 별점
     */
    private Integer rating;

    /**
     * [추가됨] 리뷰 본문
     * * - 리스트 화면에서 보여줄 리뷰 내용
     * - 프론트엔드 JS가 content 필드를 참조하여 렌더링함
     */
    private String content;
}