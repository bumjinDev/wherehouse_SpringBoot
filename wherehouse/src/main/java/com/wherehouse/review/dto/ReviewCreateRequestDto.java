package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

/**
 * 리뷰 작성 요청 DTO
 *
 * 설계 명세서: 7.3.4 리뷰 API Request/Response DTO
 *             6.2 리뷰 작성 API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequestDto {

    /**
     * 매물 식별자 (32자 MD5 Hash)
     */
    @NotBlank(message = "매물 ID는 필수입니다")
    @Size(min = 32, max = 32, message = "매물 ID는 32자여야 합니다")
    private String propertyId;

    /**
     * 작성자 ID
     */
    @NotBlank(message = "사용자 ID는 필수입니다")
    @Size(max = 50, message = "사용자 ID는 50자 이하여야 합니다")
    private String userId;

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