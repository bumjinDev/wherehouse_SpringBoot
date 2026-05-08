package com.wherehouse.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateRequestDto {

    @JsonProperty("reviewId")
    @NotNull(message = "리뷰 ID는 필수입니다")
    private Long reviewId;

    @NotBlank(message = "매물 유형은 필수입니다")
    @JsonProperty("propertyType")
    @Pattern(regexp = "^(charter|monthly)$", message = "매물 유형은 charter 또는 monthly만 가능합니다")
    private String propertyType;

    @NotNull(message = "별점은 필수입니다")
    @Min(value = 1, message = "별점은 1점 이상이어야 합니다")
    @Max(value = 5, message = "별점은 5점 이하여야 합니다")
    private Integer rating;

    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(min = 20, max = 1000, message = "리뷰 내용은 20자 이상 1000자 이하여야 합니다")
    private String content;
}