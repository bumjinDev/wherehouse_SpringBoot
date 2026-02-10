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
     */
    /**
     * 리뷰 목록 조회
     */
    public ReviewListResponseDto getReviews(ReviewListRequestDto requestDto) {

        long methodStart = System.currentTimeMillis();

        // [Step 1] 파라미터 추출
        String propertyId = requestDto.getPropertyId();
        String propertyName = requestDto.getPropertyName();
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
            long q1Start = System.currentTimeMillis();
            List<String> targetPropertyIds = reviewRepository.findPropertyIdsByName(propertyName);
            long q1End = System.currentTimeMillis();

            log.info("[PERF] findPropertyIdsByName | 소요={}ms | 입력='{}' | 결과건수={}",
                    (q1End - q1Start), propertyName, targetPropertyIds.size());

            if (targetPropertyIds.isEmpty()) {
                reviewPage = Page.empty(pageable);
            } else {
                reviewPage = reviewRepository.findByPropertyIdIn(targetPropertyIds, pageable);
            }
        } else {
            reviewPage = reviewRepository.findReviews(propertyId, keyword, pageable);
        }

        List<Review> reviews = reviewPage.getContent();

        log.info("리뷰 조회 완료: 조회 건수={}", reviews.size());

        // [Step 4] 매물 정보들 조회
        Map<String, String> propertyNameMap = getPropertyNames(reviews);

        // [Step 5] filterMeta 생성
        FilterMetaDto filterMeta = createFilterMeta(propertyId);

        // [Step 6] DTO 변환 및 응답
        List<ReviewSummaryDto> reviewDtos = reviews.stream()
                .map(review -> convertToReviewSummaryDto(review, propertyNameMap))
                .collect(Collectors.toList());

        long methodEnd = System.currentTimeMillis();
        log.info("[PERF] getReviews 총소요={}ms", (methodEnd - methodStart));

        return ReviewListResponseDto.builder()
                .filterMeta(filterMeta)
                .reviews(reviewDtos)
                .totalPages(reviewPage.getTotalPages())
                .totalElements(reviewPage.getTotalElements())
                .currentPage(requestDto.getPage())
                .build();
    }

    /**
     * 리뷰 단건 상세 조회
     */
    public ReviewDetailDto getReviewDetail(Long reviewId) {
        log.info("리뷰 상세 조회 요청: reviewId={}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + reviewId));

        return convertToReviewDetailDto(review);
    }

    // ======================================================================
    // Private Helper Methods
    // ======================================================================

    private Sort createSortCondition(String sort) {
        if ("rating_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private Map<String, String> getPropertyNames(List<Review> reviews) {
        Set<String> propertyIds = reviews.stream()
                .map(Review::getPropertyId)
                .collect(Collectors.toSet());

        if (propertyIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> results = reviewRepository.findPropertyNames(propertyIds);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (String) row[1]
                ));
    }

    private FilterMetaDto createFilterMeta(String propertyId) {
        if (propertyId == null || propertyId.isBlank()) {
            return null;
        }
        return reviewStatisticsRepository.findById(propertyId)
                .map(stats -> FilterMetaDto.builder()
                        .targetPropertyId(propertyId)
                        .propertyAvgRating(stats.getAvgRating().doubleValue())
                        .build())
                .orElse(null);
    }

    /**
     * [수정됨] ReviewSummaryDto 변환
     * * - 변경사항: .content(review.getContent()) 추가
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
                .content(review.getContent()) // [핵심 수정] 리뷰 본문 매핑
                .build();
    }

    private ReviewDetailDto convertToReviewDetailDto(Review review) {
        return ReviewDetailDto.builder()
                .reviewId(review.getReviewId())
                .propertyId(review.getPropertyId())
                .userId(maskUserId(review.getUserId()))
                .rating(review.getRating())
                .content(review.getContent())
                .build();
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) {
            return userId;
        }
        return userId.substring(0, 4) + "****";
    }
}