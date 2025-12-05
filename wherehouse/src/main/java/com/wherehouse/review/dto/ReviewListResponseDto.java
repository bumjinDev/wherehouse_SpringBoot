package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 리뷰 목록 조회 API 응답 DTO
 * * API 명세: 6.3 리뷰 목록 조회 API
 * Endpoint: GET /api/v1/reviews/list
 * Response Code: 200 OK
 * * [수정 사항]
 * 1. 서버 사이드 페이지네이션 지원을 위한 메타데이터 필드 추가
 * - totalPages: 전체 페이지 수
 * - totalElements: 전체 데이터 개수
 * - currentPage: 현재 페이지 번호
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewListResponseDto {

    /**
     * 필터링 메타 정보
     * * - 특정 매물 조회 시(propertyId 지정)에만 포함
     * - 전체 리뷰 조회 시 null
     * - 조회 대상 매물의 ID와 평균 평점 포함
     */
    private FilterMetaDto filterMeta;

    /**
     * 리뷰 데이터 리스트
     * * - 요청 조건에 맞는 리뷰 목록 (요약본)
     * - 각 리뷰는 ReviewSummaryDto 타입
     * - 빈 리스트일 수 있음
     */
    private List<ReviewSummaryDto> reviews;

    /**
     * [추가] 전체 페이지 수
     * - 프론트엔드에서 페이지네이션 버튼(1, 2, 3...) 생성 시 사용
     */
    private Integer totalPages;

    /**
     * [추가] 전체 데이터 개수
     * - "총 N개의 리뷰" 표시 용도
     */
    private Long totalElements;

    /**
     * [추가] 현재 페이지 번호
     * - 현재 보고 있는 페이지 (1부터 시작)
     * - 프론트엔드 현재 페이지 활성화 표시에 사용
     */
    private Integer currentPage;
}