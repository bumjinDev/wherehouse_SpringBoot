package com.wherehouse.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCreateRequestDto {

    @NotBlank(message = "매물 ID는 필수입니다.")
    @JsonProperty("propertyId")
    private String propertyId;

    @NotBlank(message = "매물 유형은 필수입니다.")
    @JsonProperty("propertyType")
    @Pattern(regexp = "^(charter|monthly)$", message = "매물 유형은 charter 또는 monthly만 가능합니다")
    private String propertyType;

    @NotNull(message = "별점은 필수입니다.")
    @Min(value = 1, message = "별점은 최소 1점 이상이어야 합니다.")
    @Max(value = 5, message = "별점은 최대 5점까지 가능합니다.")
    private Integer rating;

    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(min = 20, max = 1000, message = "리뷰 내용은 20자 이상 1000자 이하로 작성해주세요.")
    private String content;
}