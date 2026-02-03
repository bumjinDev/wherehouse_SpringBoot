package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 리뷰 수정 응답 DTO
 *
 * 설계 명세서: 6.5 리뷰 수정 API
 *             6.5.2 응답 (Response)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateResponseDto {

    /**
     * 수정된 리뷰 ID
     */
    private Long reviewId;

    /**
     * 수정 시각 (ISO 8601 format)
     *
     * 예: "2025-11-28T09:00:00"
     */
    private String updatedAt;
}