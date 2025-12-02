package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 리뷰 요약 정보 DTO
 *
 * 설계 명세서: 6.3.2 응답 (Response) - reviews[]
 *
 * 리뷰 목록 조회 시 사용되는 요약본
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryDto {

    /**
     * 리뷰 ID (상세 조회 Key)
     */
    private Long reviewId;

    /**
     * 매물 ID (32자 MD5 Hash)
     */
    private String propertyId;

    /**
     * 아파트/매물명
     */
    private String propertyName;

    /**
     * 작성자 ID (마스킹 처리)
     *
     * 예: "user1234" → "user****"
     */
    private String userId;

    /**
     * 별점 (1~5)
     */
    private Integer rating;

    /**
     * 내용 요약 (100자 이내)
     */
    private String summary;

    /**
     * 키워드 태그 리스트
     *
     * 예: ["채광", "조용함", "교통"]
     */
    private List<String> tags;

    /**
     * 작성 일시 (ISO 8601 format)
     *
     * 예: "2025-11-27T10:30:00"
     */
    private String createdAt;
}