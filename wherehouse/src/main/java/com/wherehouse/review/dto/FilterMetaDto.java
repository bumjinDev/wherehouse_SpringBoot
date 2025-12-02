package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 필터 메타 정보 DTO
 *
 * 설계 명세서: 6.3.2 응답 (Response) - filterMeta
 *
 * propertyId로 필터링한 경우에만 사용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterMetaDto {

    /**
     * 필터링 대상 매물 ID
     */
    private String targetPropertyId;

    /**
     * 해당 매물의 평균 평점
     */
    private Double propertyAvgRating;
}