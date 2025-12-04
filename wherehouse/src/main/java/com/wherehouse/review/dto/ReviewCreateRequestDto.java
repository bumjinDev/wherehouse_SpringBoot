package com.wherehouse.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 리뷰 작성 요청 DTO
 * * API Endpoint: POST /api/v1/reviews
 * * [수정 사항]
 * 1. MD5 해시 PK 설계를 반영하여 propertyId 타입을 String으로 변경
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCreateRequestDto {

    /**
     * 매물 ID (MD5 Hash)
     * - 프론트엔드 자동완성 목록에서 선택된 매물의 고유 식별자
     * - RDBMS 설계상 PK가 MD5 문자열(VARCHAR/CHAR)인 경우 String 필수
     * - JSON Key: "propertyId"
     */
    @NotBlank(message = "매물 ID는 필수입니다.")
    @JsonProperty("propertyId")
    private String propertyId; // [변경] Long -> String

    /**
     * 별점
     * - 1~5 사이의 정수
     */
    @NotNull(message = "별점은 필수입니다.")
    @Min(value = 1, message = "별점은 최소 1점 이상이어야 합니다.")
    @Max(value = 5, message = "별점은 최대 5점까지 가능합니다.")
    private Integer rating;

    /**
     * 리뷰 내용
     * - 최소 20자, 최대 1000자
     */
    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(min = 20, max = 1000, message = "리뷰 내용은 20자 이상 1000자 이하로 작성해주세요.")
    private String content;
}