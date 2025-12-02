package com.wherehouse.review.service;

import com.wherehouse.review.domain.Review;
import com.wherehouse.review.domain.ReviewKeyword;
import com.wherehouse.review.domain.ReviewStatistics;
import com.wherehouse.review.dto.*;
import com.wherehouse.review.repository.ReviewKeywordRepository;
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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 리뷰 조회 서비스
 *
 * 설계 명세서: 6.3 리뷰 목록 조회 API, 6.4 리뷰 단건 상세 조회 API
 *
 * propertyId 유무에 따라 특정 매물/전체 리뷰 조회를 통합 처리한다.
 * 모든 조회 작업은 읽기 전용 트랜잭션으로 실행되어 성능을 최적화한다.
 *
 * @author 정범진
 * @since 2025-11-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final ReviewStatisticsRepository reviewStatisticsRepository;
    private final ReviewKeywordRepository reviewKeywordRepository;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ========================================================================
    // Public Methods
    // ========================================================================

    /**
     * 리뷰 목록 조회 (통합)
     *
     * propertyId가 있으면 해당 매물의 리뷰만 조회 (모달용)
     * propertyId가 없으면 전체 리뷰 조회 (게시판용)
     *
     * 처리 순서:
     * 1. Pageable 생성 (1-based page를 0-based로 변환)
     * 2. propertyId 유무에 따라 Repository 메서드 분기
     * 3. Review Entity를 ReviewSummaryDto로 변환 (마스킹, 요약, 태그 추출)
     * 4. PaginationDto 생성
     * 5. ReviewListResponseDto 반환
     *
     * @param requestDto 리뷰 조회 요청 (propertyId, page, size, sort)
     * @return 리뷰 목록 응답 (filterMeta, reviews, pagination)
     */
    public ReviewListResponseDto getReviews(ReviewQueryRequestDto requestDto) {

        Pageable pageable = createPageable(requestDto);

        Page<Review> reviewPage;
        FilterMetaDto filterMeta = null;

        if (requestDto.getPropertyId() != null && !requestDto.getPropertyId().isEmpty()) {
            reviewPage = reviewRepository.findByPropertyId(requestDto.getPropertyId(), pageable);
            filterMeta = createFilterMeta(requestDto.getPropertyId());

            log.info("특정 매물 리뷰 조회: propertyId={}, totalItems={}",
                    requestDto.getPropertyId(), reviewPage.getTotalElements());
        } else {
            reviewPage = reviewRepository.findAll(pageable);
            log.info("전체 리뷰 조회: totalItems={}", reviewPage.getTotalElements());
        }

        List<ReviewSummaryDto> reviewSummaries = reviewPage.getContent().stream()
                .map(this::convertToSummaryDto)
                .collect(Collectors.toList());

        PaginationDto pagination = createPaginationDto(reviewPage, requestDto.getPage());

        return ReviewListResponseDto.builder()
                .filterMeta(filterMeta)
                .reviews(reviewSummaries)
                .pagination(pagination)
                .build();
    }

    /**
     * 리뷰 단건 상세 조회
     *
     * 설계 명세서: 6.4 리뷰 단건 상세 조회 API
     *
     * 특정 리뷰의 전체 내용(Full Text)을 조회한다.
     * 목록 조회와 달리 내용 요약 없이 전체 원문을 반환한다.
     *
     * @param reviewId 리뷰 ID
     * @return ReviewDetailDto (전체 원문 포함)
     * @throws IllegalArgumentException 리뷰가 존재하지 않는 경우
     */
    public ReviewDetailDto getReviewDetail(Long reviewId) {

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + reviewId));

        log.info("리뷰 상세 조회: reviewId={}, propertyId={}", reviewId, review.getPropertyId());

        List<String> tags = extractTags(reviewId);

        return ReviewDetailDto.builder()
                .reviewId(review.getReviewId())
                .propertyId(review.getPropertyId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .content(review.getContent())
                .tags(tags)
                .createdAt(review.getCreatedAt().format(ISO_FORMATTER))
                .updatedAt(review.getUpdatedAt() != null
                        ? review.getUpdatedAt().format(ISO_FORMATTER)
                        : null)
                .build();
    }

    // ========================================================================
    // Private Methods
    // ========================================================================

    /**
     * Pageable 객체 생성
     *
     * 1-based 페이지 번호를 0-based로 변환하고 Sort 객체를 생성한다.
     *
     * @param requestDto 리뷰 조회 요청 DTO
     * @return Pageable 객체
     */
    private Pageable createPageable(ReviewQueryRequestDto requestDto) {
        int pageIndex = requestDto.getPage() - 1;
        Sort sort = createSort(requestDto.getSort());
        return PageRequest.of(pageIndex, requestDto.getSize(), sort);
    }

    /**
     * 정렬 기준에 따른 Sort 객체 생성
     *
     * 지원 정렬:
     * - latest: createdAt DESC
     * - rating_desc: rating DESC, createdAt DESC
     * - rating_asc: rating ASC, createdAt DESC
     *
     * @param sortParam 정렬 파라미터
     * @return Sort 객체
     */
    private Sort createSort(String sortParam) {
        return switch (sortParam) {
            case "rating_desc" -> Sort.by(
                    Sort.Order.desc("rating"),
                    Sort.Order.desc("createdAt")
            );
            case "rating_asc" -> Sort.by(
                    Sort.Order.asc("rating"),
                    Sort.Order.desc("createdAt")
            );
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };
    }

    /**
     * 특정 매물의 평균 평점 정보 조회
     *
     * REVIEW_STATISTICS 테이블에서 해당 매물의 평균 평점을 조회한다.
     * 통계 데이터가 없으면 평균 평점을 0.0으로 반환한다.
     *
     * @param propertyId 매물 ID
     * @return FilterMetaDto (매물 ID, 평균 평점)
     */
    private FilterMetaDto createFilterMeta(String propertyId) {
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElse(null);

        Double avgRating = (statistics != null)
                ? statistics.getAvgRating().doubleValue()
                : 0.0;

        return FilterMetaDto.builder()
                .targetPropertyId(propertyId)
                .propertyAvgRating(avgRating)
                .build();
    }

    /**
     * Review 엔티티를 ReviewSummaryDto로 변환
     *
     * 변환 작업:
     * - userId 마스킹 처리 (예: "user1234" -> "user****")
     * - content 100자 이내로 요약
     * - REVIEW_KEYWORDS 테이블에서 키워드 태그 추출
     *
     * TODO: propertyName 조회 로직 추가 필요 (현재 임시값)
     *
     * @param review Review 엔티티
     * @return ReviewSummaryDto
     */
    private ReviewSummaryDto convertToSummaryDto(Review review) {
        String maskedUserId = maskUserId(review.getUserId());
        String summary = createSummary(review.getContent());
        List<String> tags = extractTags(review.getReviewId());
        String propertyName = "매물명";

        return ReviewSummaryDto.builder()
                .reviewId(review.getReviewId())
                .propertyId(review.getPropertyId())
                .propertyName(propertyName)
                .userId(maskedUserId)
                .rating(review.getRating())
                .summary(summary)
                .tags(tags)
                .createdAt(review.getCreatedAt().format(ISO_FORMATTER))
                .build();
    }

    /**
     * 사용자 ID 마스킹 처리
     *
     * 앞 4자만 노출하고 나머지는 **** 처리
     * 예: "user1234" -> "user****"
     *
     * @param userId 원본 사용자 ID
     * @return 마스킹된 사용자 ID
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) {
            return "****";
        }
        return userId.substring(0, 4) + "****";
    }

    /**
     * 리뷰 내용 요약 생성
     *
     * 100자를 초과하는 경우 100자까지만 자르고 "..." 추가
     *
     * @param content 원본 리뷰 내용
     * @return 요약된 내용
     */
    private String createSummary(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= 100) {
            return content;
        }
        return content.substring(0, 100) + "...";
    }

    /**
     * 리뷰의 키워드 태그 추출
     *
     * REVIEW_KEYWORDS 테이블에서 해당 리뷰의 모든 키워드를 조회한다.
     *
     * @param reviewId 리뷰 ID
     * @return 키워드 리스트
     */
    private List<String> extractTags(Long reviewId) {
        List<ReviewKeyword> keywords = reviewKeywordRepository.findByReviewId(reviewId);
        return keywords.stream()
                .map(ReviewKeyword::getKeyword)
                .collect(Collectors.toList());
    }

    /**
     * Spring Data Page를 PaginationDto로 변환
     *
     * @param page Spring Data Page 객체
     * @param requestedPage 요청된 페이지 번호 (1-based)
     * @return PaginationDto
     */
    private PaginationDto createPaginationDto(Page<?> page, int requestedPage) {
        return PaginationDto.builder()
                .currentPage(requestedPage)
                .totalPages(page.getTotalPages())
                .totalItems(page.getTotalElements())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}