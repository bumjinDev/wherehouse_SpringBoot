package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;

/**
 * 리뷰 조회 요청 DTO
 *
 * 설계 명세서 6.3절 기반이나, Query Parameter 대신 Request Body 방식으로 변경
 *
 * 동작 방식:
 * - propertyId 포함 시: 해당 매물의 리뷰만 필터링 (모달용)
 * - propertyId 미포함 시: 전체 리뷰 최신순 조회 (게시판용)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQueryRequestDto {

    /**
     */
    @Size(min = 32, max = 32, message = "매물 ID는 32자여야 합니다")
    private String propertyId;

    /**
     * 페이지 번호 (1부터 시작)
     */
    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    private Integer page = 1;

    /**
     * 정렬 기준
     *
     * - latest: 최신순 (createdAt DESC) - 기본값
     * - rating_desc: 별점 높은순 (rating DESC, createdAt DESC)
     * - rating_asc: 별점 낮은순 (rating ASC, createdAt DESC)
     */
    @Pattern(regexp = "^(rating_desc|rating_asc)$",
            message = "정렬 기준은 rating_desc, rating_asc 중 하나여야 합니다")
    private String sort = "rating_desc";


    /**
     * 검색어
     */
    private String searchType = "all";
}