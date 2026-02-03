package com.wherehouse.review.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 리뷰 목록 조회 API 요청 DTO
 * * API 명세: 6.3 리뷰 목록 조회 API
 * Endpoint: GET /api/v1/reviews/list
 * * [수정 사항]
 * 1. 매물 이름 검색을 위한 propertyName 필드 추가
 * 2. propertyId는 JSON/Query Parameter 매핑을 위해 @JsonProperty("propertyId") 명시 (일관성 유지)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewListRequestDto {

    /**
     * 매물 식별자 (MD5 Hash)
     * * - 선택 필드 (전체 조회 또는 이름 검색 시 null)
     * - 검증: 빈 문자열 OR 32자 16진수
     * - JS에서 propertyId로 보낼 경우 자동 매핑되지만, 명시적으로 선언
     */
    @JsonProperty("propertyId")
    @Pattern(regexp = "^$|^[a-fA-F0-9]{32}$", message = "매물 ID는 32자 MD5 형식이어야 합니다")
    private String propertyId;

    /**
     * [추가됨] 매물 이름 검색어
     * * - 선택 필드
     * - 사용자가 입력한 매물 이름 (예: "삼성")
     * - propertyId가 없고 이 필드가 존재하면 "이름 검색 로직"으로 분기
     */
    private String propertyName;

    /**
     * 페이지 번호
     * * - 선택 필드 (Default: 1)
     * - 검증: 1 이상 (Null 허용)
     */
    @Min(value = 1,  message = "페이지 번호는 1 이상이어야 합니다")
    @Builder.Default
    private Integer page = 1;

    /**
     * 정렬 기준
     * * - 선택 필드 (Default: rating_desc)
     * - rating_desc: 최신 작성 날짜순 (기본값)
     * - rating_asc: 가장 먼저 작성한 순
     * - 검증: 빈 문자열 OR 정해진 정렬 키워드
     */
    @Pattern(regexp = "^$|^(rating_desc|rating_asc)$", message = "정렬 기준은 rating_desc 또는 rating_asc여야 합니다")
    @Builder.Default
    private String sort = "rating_desc";

    /**
     * 검색 키워드 (리뷰 내용/태그 검색용)
     * * - 선택 필드
     * - 별도 검증 없음 (모든 문자열 허용)
     */
    private String keyword;
}