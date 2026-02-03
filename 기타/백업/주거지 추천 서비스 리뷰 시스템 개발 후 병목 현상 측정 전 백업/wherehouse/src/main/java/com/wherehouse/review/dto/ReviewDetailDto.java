package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 리뷰 상세 조회 응답 DTO
 *
 * 설계 명세서: 6.4 리뷰 단건 상세 조회 API
 *
 * 리뷰의 전체 내용(Full Text)을 반환한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDetailDto {

    /**
     * 리뷰 ID
     */
    private Long reviewId;

    /**
     * 매물 ID (32자 MD5 Hash)
     */
    private String propertyId;

    /**
     * 작성자 ID (마스킹 없이 원본)
     */
    private String userId;

    /**
     * 별점 (1~5)
     */
    private Integer rating;

    /**
     * 리뷰 내용 (전체 원문)
     */
    private String content;

    /**
     * 키워드 태그 리스트
     */
    private List<String> tags;

    /**
     * 작성 일시 (ISO 8601 format)
     */
    private String createdAt;

    /**
     * 수정 일시 (ISO 8601 format)
     * 수정되지 않은 경우 null
     */
    private String updatedAt;
}