package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

/**
 * 리뷰 수정 요청 DTO
 *
 * 설계 명세서: 6.5 리뷰 수정 API (재설계)
 *             6.5.1 요청 (Request)
 *
 * POST 방식에 맞춰 reviewId를 Request Body에 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateRequestDto {

    /**
     * 수정할 리뷰 ID
     */
    @NotNull(message = "리뷰 ID는 필수입니다")
    private Long reviewId;

    /**
     * 별점 (1~5 정수)
     */
    @NotNull(message = "별점은 필수입니다")
    @Min(value = 1, message = "별점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "별점은 5점 이하여야 합니다")
    private Integer rating;

    /**
     * 리뷰 내용 (20자 이상 1000자 이하)
     */
    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(min = 20, max = 1000, message = "리뷰 내용은 20자 이상 1000자 이하여야 합니다")
    private String content;
}