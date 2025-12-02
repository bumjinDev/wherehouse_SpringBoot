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
     * 매물 식별자 (32자 MD5 Hash)
     *
     * null인 경우: 전체 리뷰 조회 (게시판용)
     * 값이 있는 경우: 해당 매물의 리뷰만 조회 (모달용)
     */
    @Size(min = 32, max = 32, message = "매물 ID는 32자여야 합니다")
    private String propertyId;

    /**
     * 페이지 번호 (1부터 시작)
     */
    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다")
    private Integer page = 1;

    /**
     * 페이지당 항목 수
     */
    @Min(value = 1, message = "페이지당 항목 수는 1 이상이어야 합니다")
    @Max(value = 100, message = "페이지당 항목 수는 100 이하여야 합니다")
    private Integer size = 10;

    /**
     * 정렬 기준
     *
     * - latest: 최신순 (createdAt DESC) - 기본값
     * - rating_desc: 별점 높은순 (rating DESC, createdAt DESC)
     * - rating_asc: 별점 낮은순 (rating ASC, createdAt DESC)
     */
    @Pattern(regexp = "^(latest|rating_desc|rating_asc)$",
            message = "정렬 기준은 latest, rating_desc, rating_asc 중 하나여야 합니다")
    private String sort = "latest";

    /**
     * 검색어 (매물명 또는 리뷰 내용 검색)
     */
    @Size(max = 100, message = "검색어는 100자 이하여야 합니다")
    private String keyword;

    /**
     * 검색 조건
     *
     * - all: 전체 검색 (기본값)
     * - content: 리뷰 내용 검색
     * - tag: 키워드 태그 검색
     */
    @Pattern(regexp = "^(all|content|tag)$",
            message = "검색 조건은 all, content, tag 중 하나여야 합니다")
    private String searchType = "all";
}