package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 리뷰 목록 조회 응답 DTO
 *
 * 설계 명세서: 6.3.2 응답 (Response)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewListResponseDto {

    /**
     * 필터링 메타 정보
     *
     * propertyId로 필터링한 경우에만 값이 존재
     * 전체 리뷰 조회 시에는 null
     */
    private FilterMetaDto filterMeta;

    /**
     * 리뷰 데이터 리스트 (요약본)
     */
    private List<ReviewSummaryDto> reviews;

    /**
     * 페이징 정보
     */
    private PaginationDto pagination;
}