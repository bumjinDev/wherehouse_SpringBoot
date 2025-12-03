package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 필터 메타 정보 DTO
 * 
 * 용도: 특정 매물 조회 시 해당 매물의 메타 정보 제공
 * 부모 DTO: ReviewListResponseDto
 * 
 * 주의:
 * - propertyId가 지정된 요청에서만 포함
 * - 전체 리뷰 조회 시에는 null
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterMetaDto {
    
    /**
     * 조회 대상 매물 ID
     * 
     * - Request의 propertyId와 동일한 값
     * - 32자 MD5 Hash
     */
    private String targetPropertyId;
    
    /**
     * 해당 매물의 평균 평점
     * 
     * - Double 타입
     * - 소수점 첫째 자리까지 표시
     * - 예시: 4.5
     */
    private Double propertyAvgRating;
}
