package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 페이징 정보 DTO
 *
 * 설계 명세서: 6.3.2 응답 (Response) - pagination
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationDto {

    /**
     * 현재 페이지 번호 (1부터 시작)
     */
    private Integer currentPage;

    /**
     * 전체 페이지 수
     */
    private Integer totalPages;

    /**
     * 전체 항목 수
     */
    private Long totalItems;

    /**
     * 페이지당 항목 수
     */
    private Integer pageSize;

    /**
     * 다음 페이지 존재 여부
     */
    private Boolean hasNext;

    /**
     * 이전 페이지 존재 여부
     */
    private Boolean hasPrevious;
}