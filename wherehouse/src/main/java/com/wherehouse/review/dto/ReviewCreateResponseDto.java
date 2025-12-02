package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 리뷰 작성 응답 DTO
 *
 * 설계 명세서: 7.3.4 리뷰 API Request/Response DTO
 *             6.2 리뷰 작성 API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateResponseDto {

    /**
     * 생성된 리뷰 ID
     */
    private Long reviewId;

    /**
     * 작성 시각 (ISO 8601 format)
     */
    private String createdAt;
}