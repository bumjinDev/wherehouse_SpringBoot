package com.wherehouse.review.service;

import com.wherehouse.review.domain.Review;
import com.wherehouse.review.dto.FilterMetaDto;
import com.wherehouse.review.dto.ReviewDetailDto;
import com.wherehouse.review.dto.ReviewListRequestDto;
import com.wherehouse.review.dto.ReviewListResponseDto;
import com.wherehouse.review.dto.ReviewSummaryDto;
import com.wherehouse.review.repository.ReviewRepository;
import com.wherehouse.review.repository.ReviewStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 리뷰 조회 서비스
 *
 * 설계 명세서: 6.3 리뷰 목록 조회 API, 6.4 리뷰 단건 상세 조회 API
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final ReviewStatisticsRepository reviewStatisticsRepository;

    /**
     * 리뷰 목록 조회
     *
     * 데이터 소스: RDB (Redis 미사용)
     *
     * @param requestDto 리뷰 조회 요청
     * @return 리뷰 목록 응답
     */
    public ReviewListResponseDto getReviews(ReviewListRequestDto requestDto) {

        // [Step 1] 파라미터 추출
        String propertyId = requestDto.getPropertyId();
        String propertyName = requestDto.getPropertyName(); // 매물 이름 검색어 추가
        String keyword = requestDto.getKeyword();
        Integer page = requestDto.getPage();
        String sort = requestDto.getSort();

        log.info("리뷰 조회 시작: propertyId={}, propertyName={}, keyword={}, page={}, sort={}",
                propertyId, propertyName, keyword, page, sort);

        // [Step 2] 정렬 조건 생성
        Sort sortCondition = createSortCondition(sort);
        Pageable pageable = PageRequest.of(page - 1, 10, sortCondition);

        // [Step 3] 검색 조건에 따른 조회 분기 처리
        Page<Review> reviewPage;

        if (propertyName != null && !propertyName.isBlank()) {
            // Case 1: 매물 이름으로 검색 (이름 -> ID 목록 -> 리뷰 조회)
            List<String> targetPropertyIds = reviewRepository.findPropertyIdsByName(propertyName);

            if (targetPropertyIds.isEmpty()) {
                // 검색된 매물이 없으면 빈 페이지 반환
                reviewPage = Page.empty(pageable);
            } else {
                // 검색된 ID 목록으로 리뷰 조회 (IN 절)
                reviewPage = reviewRepository.findByPropertyIdIn(targetPropertyIds, pageable);
            }
        } else {
            // Case 2: 매물 ID 검색 (propertyId 존재 시)
            // Case 3: 전체 리뷰 조회 (propertyId null 시)
            // 기존 findReviews 메서드가 이 두 가지 케이스를 모두 처리함
            reviewPage = reviewRepository.findReviews(
                    propertyId,
                    keyword,
                    pageable
            );
        }

        List<Review> reviews = reviewPage.getContent();

        log.info("리뷰 조회 완료: 조회 건수={}", reviews.size());

        // [Step 4] 매물 정보들 조회 : 리뷰 조회 결과로부터 매물 ID를 추출하고 실제 매물명을 조회
        Map<String, String> propertyNameMap = getPropertyNames(reviews);

        // [Step 5] filterMeta 생성 (propertyId 지정 시만, 지정 안되면 null)
        // 이름 검색의 경우 특정 단일 매물이 아니므로 filterMeta는 null로 처리됨
        FilterMetaDto filterMeta = createFilterMeta(propertyId);

        // [Step 6] DTO 변환 및 응답
        List<ReviewSummaryDto> reviewDtos = reviews.stream()
                .map(review -> convertToReviewSummaryDto(review, propertyNameMap))
                .collect(Collectors.toList());

        return ReviewListResponseDto.builder()
                .filterMeta(filterMeta)
                .reviews(reviewDtos)
                .build();
    }

    /**
     * 리뷰 단건 상세 조회
     *
     * 설계 명세서: 6.4 리뷰 단건 상세 조회 API
     *
     * @param reviewId 리뷰 ID
     * @return 리뷰 상세 정보
     */
    public ReviewDetailDto getReviewDetail(Long reviewId) {

        log.info("리뷰 상세 조회 요청: reviewId={}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + reviewId));

        log.info("리뷰 상세 조회 완료: reviewId={}", reviewId);

        return convertToReviewDetailDto(review);
    }

    /**
     * 정렬 조건 생성
     *
     * 명세서 495행:
     * - rating_desc: 최신 작성 날짜 (기본값)
     * - rating_asc: 가장 먼저 작성한순
     */
    private Sort createSortCondition(String sort) {
        if ("rating_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        // 기본값: rating_desc (최신순)
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    /**
     * RDB에서 매물명 일괄 조회
     *
     * 데이터 소스: PROPERTIES_CHARTER, PROPERTIES_MONTHLY 테이블 (UNION)
     *
     * @param reviews 리뷰 목록
     * @return Map<PropertyId, PropertyName>
     */
    private Map<String, String> getPropertyNames(List<Review> reviews) {
        // 매물 ID 목록 추출
        Set<String> propertyIds = reviews.stream()
                .map(Review::getPropertyId)
                .collect(Collectors.toSet());

        if (propertyIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // RDB에서 매물명 일괄 조회 (UNION 쿼리)
        List<Object[]> results = reviewRepository.findPropertyNames(propertyIds);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],  // property_id
                        row -> (String) row[1]   // apt_nm
                ));
    }

    /**
     * filterMeta 생성
     *
     * 데이터 소스: RDB의 REVIEW_STATISTICS 테이블
     *
     * @param propertyId 매물 ID
     * @return filterMeta (propertyId가 null이면 null 반환)
     */
    private FilterMetaDto createFilterMeta(String propertyId) {
        if (propertyId == null || propertyId.isBlank()) {
            return null;
        }

        // RDB의 REVIEW_STATISTICS 테이블에서 리뷰 통계 데이터 조회.
        return reviewStatisticsRepository.findById(propertyId)
                .map(stats -> FilterMetaDto.builder()
                        .targetPropertyId(propertyId)
                        .propertyAvgRating(stats.getAvgRating().doubleValue())
                        .build())
                .orElse(null);
    }

    /**
     * ReviewSummaryDto 변환
     *
     * @param review 리뷰 엔티티
     * @param propertyNameMap 매물명 Map
     * @return ReviewSummaryDto
     */
    private ReviewSummaryDto convertToReviewSummaryDto(
            Review review,
            Map<String, String> propertyNameMap) {

        return ReviewSummaryDto.builder()
                .reviewId(review.getReviewId())
                .propertyId(review.getPropertyId())
                .propertyName(propertyNameMap.getOrDefault(review.getPropertyId(), "알 수 없음"))
                .userId(maskUserId(review.getUserId()))
                .rating(review.getRating())
                .build();
    }

    /**
     * ReviewDetailDto 변환
     *
     * @param review 리뷰 엔티티
     * @return ReviewDetailDto
     */
    private ReviewDetailDto convertToReviewDetailDto(Review review) {
        return ReviewDetailDto.builder()
                .reviewId(review.getReviewId())
                .propertyId(review.getPropertyId())
                .userId(maskUserId(review.getUserId()))
                .rating(review.getRating())
                .content(review.getContent())
                .build();
    }

    /**
     * userId 마스킹 처리
     *
     * 명세서 523행: "user****" 형식
     *
     * @param userId 원본 사용자 ID
     * @return 마스킹된 사용자 ID
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) {
            return userId;
        }
        return userId.substring(0, 4) + "****";
    }
}