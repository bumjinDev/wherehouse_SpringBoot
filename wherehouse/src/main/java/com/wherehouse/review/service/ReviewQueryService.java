package com.wherehouse.review.service;

import com.wherehouse.review.domain.ReviewBase;
import com.wherehouse.review.dto.FilterMetaDto;
import com.wherehouse.review.dto.ReviewDetailDto;
import com.wherehouse.review.dto.ReviewListRequestDto;
import com.wherehouse.review.dto.ReviewListResponseDto;
import com.wherehouse.review.dto.ReviewSummaryDto;
import com.wherehouse.review.repository.ReviewCharterRepository;
import com.wherehouse.review.repository.ReviewMonthlyRepository;
import com.wherehouse.review.repository.ReviewStatisticsCharterRepository;
import com.wherehouse.review.repository.ReviewStatisticsMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewCharterRepository reviewCharterRepository;
    private final ReviewMonthlyRepository reviewMonthlyRepository;
    private final ReviewStatisticsCharterRepository reviewStatisticsCharterRepository;
    private final ReviewStatisticsMonthlyRepository reviewStatisticsMonthlyRepository;

    /**
     * 리뷰 목록 조회
     *
     * propertyType에 따라 전세/월세 리포지토리로 분기하여 조회한다.
     */
    public ReviewListResponseDto getReviews(ReviewListRequestDto requestDto) {

        long methodStart = System.currentTimeMillis();

        String propertyType = requestDto.getPropertyType();
        String propertyId = requestDto.getPropertyId();
        String propertyName = requestDto.getPropertyName();
        String keyword = requestDto.getKeyword();
        Integer page = requestDto.getPage();
        String sort = requestDto.getSort();

        log.info("리뷰 조회 시작: propertyType={}, propertyId={}, propertyName={}, keyword={}, page={}, sort={}",
                propertyType, propertyId, propertyName, keyword, page, sort);

        Sort sortCondition = createSortCondition(sort);
        Pageable pageable = PageRequest.of(page - 1, 10, sortCondition);

        boolean isCharter = "charter".equalsIgnoreCase(propertyType);

        // [Step 1] 검색 조건에 따른 조회 분기
        Page<? extends ReviewBase> reviewPage;

        if (propertyName != null && !propertyName.isBlank()) {
            long q1Start = System.currentTimeMillis();

            List<String> targetPropertyIds = isCharter
                    ? reviewCharterRepository.findPropertyIdsByName(propertyName)
                    : reviewMonthlyRepository.findPropertyIdsByName(propertyName);

            log.info("[PERF] findPropertyIdsByName | 소요={}ms | 입력='{}' | 결과건수={}",
                    (System.currentTimeMillis() - q1Start), propertyName, targetPropertyIds.size());

            if (targetPropertyIds.isEmpty()) {
                reviewPage = Page.empty(pageable);
            } else {
                reviewPage = isCharter
                        ? reviewCharterRepository.findByPropertyIdIn(targetPropertyIds, pageable)
                        : reviewMonthlyRepository.findByPropertyIdIn(targetPropertyIds, pageable);
            }
        } else {
            reviewPage = isCharter
                    ? reviewCharterRepository.findReviews(propertyId, keyword, pageable)
                    : reviewMonthlyRepository.findReviews(propertyId, keyword, pageable);
        }

        List<? extends ReviewBase> reviews = reviewPage.getContent();
        log.info("리뷰 조회 완료: 조회 건수={}", reviews.size());

        // [Step 2] 매물명 조회
        Map<String, String> propertyNameMap = getPropertyNames(reviews, isCharter);

        // [Step 3] filterMeta 생성
        FilterMetaDto filterMeta = createFilterMeta(propertyId, isCharter);

        // [Step 4] DTO 변환
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
     *
     * @param reviewId 리뷰 ID
     * @param propertyType "charter" 또는 "monthly"
     */
    public ReviewDetailDto getReviewDetail(Long reviewId, String propertyType) {
        log.info("리뷰 상세 조회 요청: reviewId={}, propertyType={}", reviewId, propertyType);

        boolean isCharter = "charter".equalsIgnoreCase(propertyType);

        ReviewBase review = isCharter
                ? reviewCharterRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId))
                : reviewMonthlyRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId));

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

    private Map<String, String> getPropertyNames(List<? extends ReviewBase> reviews, boolean isCharter) {
        Set<String> propertyIds = reviews.stream()
                .map(ReviewBase::getPropertyId)
                .collect(Collectors.toSet());

        if (propertyIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> results = isCharter
                ? reviewCharterRepository.findPropertyNames(propertyIds)
                : reviewMonthlyRepository.findPropertyNames(propertyIds);

        Map<String, String> resultMap = new HashMap<>();
        for (Object[] row : results) {
            resultMap.put((String) row[0], (String) row[1]);
        }
        return resultMap;
    }

    private FilterMetaDto createFilterMeta(String propertyId, boolean isCharter) {
        if (propertyId == null || propertyId.isBlank()) {
            return null;
        }

        if (isCharter) {
            return reviewStatisticsCharterRepository.findById(propertyId)
                    .map(stats -> FilterMetaDto.builder()
                            .targetPropertyId(propertyId)
                            .propertyAvgRating(stats.getAvgRating().doubleValue())
                            .build())
                    .orElse(null);
        } else {
            return reviewStatisticsMonthlyRepository.findById(propertyId)
                    .map(stats -> FilterMetaDto.builder()
                            .targetPropertyId(propertyId)
                            .propertyAvgRating(stats.getAvgRating().doubleValue())
                            .build())
                    .orElse(null);
        }
    }

    private ReviewSummaryDto convertToReviewSummaryDto(
            ReviewBase review,
            Map<String, String> propertyNameMap) {

        return ReviewSummaryDto.builder()
                .reviewId(review.getReviewId())
                .propertyId(review.getPropertyId())
                .propertyName(propertyNameMap.getOrDefault(review.getPropertyId(), "알 수 없음"))
                .userId(maskUserId(review.getUserId()))
                .rating(review.getRating())
                .content(review.getContent())
                .build();
    }

    private ReviewDetailDto convertToReviewDetailDto(ReviewBase review) {
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
