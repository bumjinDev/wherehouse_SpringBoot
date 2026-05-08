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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * propertyType 분기:
     *  - "charter"  → 전세 리포지토리만 조회
     *  - "monthly"  → 월세 리포지토리만 조회
     *  - null/빈값  → 양쪽 모두 조회 후 병합
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
        boolean isMonthly = "monthly".equalsIgnoreCase(propertyType);

        Page<? extends ReviewBase> reviewPage;

        if (isCharter) {
            reviewPage = queryCharter(propertyName, propertyId, keyword, pageable);
        } else if (isMonthly) {
            reviewPage = queryMonthly(propertyName, propertyId, keyword, pageable);
        } else {
            reviewPage = queryCombined(propertyName, propertyId, keyword, sort, pageable);
        }

        List<? extends ReviewBase> reviews = reviewPage.getContent();
        log.info("리뷰 조회 완료: 조회 건수={}", reviews.size());

        Map<String, String> propertyNameMap = getPropertyNames(reviews, propertyType);
        FilterMetaDto filterMeta = createFilterMeta(propertyId, propertyType);

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
     * propertyType이 null이면 전세 → 월세 순서로 탐색한다.
     */
    public ReviewDetailDto getReviewDetail(Long reviewId, String propertyType) {
        log.info("리뷰 상세 조회 요청: reviewId={}, propertyType={}", reviewId, propertyType);

        boolean isCharter = "charter".equalsIgnoreCase(propertyType);
        boolean isMonthly = "monthly".equalsIgnoreCase(propertyType);

        ReviewBase review;

        if (isCharter) {
            review = reviewCharterRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId));
        } else if (isMonthly) {
            review = reviewMonthlyRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId));
        } else {
            Optional<? extends ReviewBase> found = reviewCharterRepository.findById(reviewId);
            if (found.isEmpty()) {
                found = reviewMonthlyRepository.findById(reviewId);
            }
            review = found.orElseThrow(() ->
                    new IllegalArgumentException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId));
        }

        return convertToReviewDetailDto(review);
    }

    // ======================================================================
    // 단일 리포지토리 조회
    // ======================================================================

    private Page<? extends ReviewBase> queryCharter(
            String propertyName, String propertyId, String keyword, Pageable pageable) {

        if (propertyName != null && !propertyName.isBlank()) {
            List<String> ids = reviewCharterRepository.findPropertyIdsByName(propertyName);
            return ids.isEmpty()
                    ? Page.empty(pageable)
                    : reviewCharterRepository.findByPropertyIdIn(ids, pageable);
        }
        return reviewCharterRepository.findReviews(propertyId, keyword, pageable);
    }

    private Page<? extends ReviewBase> queryMonthly(
            String propertyName, String propertyId, String keyword, Pageable pageable) {

        if (propertyName != null && !propertyName.isBlank()) {
            List<String> ids = reviewMonthlyRepository.findPropertyIdsByName(propertyName);
            return ids.isEmpty()
                    ? Page.empty(pageable)
                    : reviewMonthlyRepository.findByPropertyIdIn(ids, pageable);
        }
        return reviewMonthlyRepository.findReviews(propertyId, keyword, pageable);
    }

    // ======================================================================
    // 통합 조회 (propertyType 미지정)
    // ======================================================================

    private Page<ReviewBase> queryCombined(
            String propertyName, String propertyId, String keyword,
            String sort, Pageable pageable) {

        Page<? extends ReviewBase> charterPage;
        Page<? extends ReviewBase> monthlyPage;

        if (propertyName != null && !propertyName.isBlank()) {
            List<String> charterIds = reviewCharterRepository.findPropertyIdsByName(propertyName);
            List<String> monthlyIds = reviewMonthlyRepository.findPropertyIdsByName(propertyName);

            charterPage = charterIds.isEmpty()
                    ? Page.empty(pageable)
                    : reviewCharterRepository.findByPropertyIdIn(charterIds, pageable);
            monthlyPage = monthlyIds.isEmpty()
                    ? Page.empty(pageable)
                    : reviewMonthlyRepository.findByPropertyIdIn(monthlyIds, pageable);
        } else {
            charterPage = reviewCharterRepository.findReviews(propertyId, keyword, pageable);
            monthlyPage = reviewMonthlyRepository.findReviews(propertyId, keyword, pageable);
        }

        List<ReviewBase> merged = new ArrayList<>();
        merged.addAll(charterPage.getContent());
        merged.addAll(monthlyPage.getContent());

        boolean ascending = "rating_asc".equals(sort);
        Comparator<LocalDateTime> dateOrder = ascending
                ? Comparator.naturalOrder()
                : Comparator.<LocalDateTime>reverseOrder();
        merged.sort(Comparator.comparing(ReviewBase::getCreatedAt, Comparator.nullsLast(dateOrder)));

        if (merged.size() > pageable.getPageSize()) {
            merged = new ArrayList<>(merged.subList(0, pageable.getPageSize()));
        }

        long totalElements = charterPage.getTotalElements() + monthlyPage.getTotalElements();
        return new PageImpl<>(merged, pageable, totalElements);
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

    private Map<String, String> getPropertyNames(List<? extends ReviewBase> reviews, String propertyType) {
        Set<String> propertyIds = reviews.stream()
                .map(ReviewBase::getPropertyId)
                .collect(Collectors.toSet());

        if (propertyIds.isEmpty()) {
            return Collections.emptyMap();
        }

        boolean isCharter = "charter".equalsIgnoreCase(propertyType);
        boolean isMonthly = "monthly".equalsIgnoreCase(propertyType);
        boolean isCombined = !isCharter && !isMonthly;

        Map<String, String> result = new HashMap<>();

        if (isCharter || isCombined) {
            for (Object[] row : reviewCharterRepository.findPropertyNames(propertyIds)) {
                result.put((String) row[0], (String) row[1]);
            }
        }
        if (isMonthly || isCombined) {
            for (Object[] row : reviewMonthlyRepository.findPropertyNames(propertyIds)) {
                result.putIfAbsent((String) row[0], (String) row[1]);
            }
        }

        return result;
    }

    private FilterMetaDto createFilterMeta(String propertyId, String propertyType) {
        if (propertyId == null || propertyId.isBlank()) {
            return null;
        }

        boolean isCharter = "charter".equalsIgnoreCase(propertyType);
        boolean isMonthly = "monthly".equalsIgnoreCase(propertyType);

        if (isCharter) {
            return reviewStatisticsCharterRepository.findById(propertyId)
                    .map(s -> buildFilterMetaDto(propertyId, s.getAvgRating().doubleValue()))
                    .orElse(null);
        }
        if (isMonthly) {
            return reviewStatisticsMonthlyRepository.findById(propertyId)
                    .map(s -> buildFilterMetaDto(propertyId, s.getAvgRating().doubleValue()))
                    .orElse(null);
        }

        // 타입 미지정 → 전세 먼저, 없으면 월세
        Optional<FilterMetaDto> meta = reviewStatisticsCharterRepository.findById(propertyId)
                .map(s -> buildFilterMetaDto(propertyId, s.getAvgRating().doubleValue()));
        if (meta.isPresent()) {
            return meta.get();
        }
        return reviewStatisticsMonthlyRepository.findById(propertyId)
                .map(s -> buildFilterMetaDto(propertyId, s.getAvgRating().doubleValue()))
                .orElse(null);
    }

    private FilterMetaDto buildFilterMetaDto(String propertyId, double avgRating) {
        return FilterMetaDto.builder()
                .targetPropertyId(propertyId)
                .propertyAvgRating(avgRating)
                .build();
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
