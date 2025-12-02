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
 * 설계 명세서: 6.3 리뷰 목록 조회 API (통합)
 *
 * 기능:
 * - propertyId 유무에 따른 통합 조회
 * - 페이징, 정렬, 검색 처리
 * - DTO 변환 (마스킹, 요약, 태그 추출)
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

    /**
     * 리뷰 목록 조회 (통합)
     *
     * @param requestDto 리뷰 조회 요청 DTO
     * @return 리뷰 목록 응답 DTO
     */
    public ReviewListResponseDto getReviews(ReviewQueryRequestDto requestDto) {

        // 1. Pageable 생성
        Pageable pageable = createPageable(requestDto);

        // 2. 리뷰 조회 (propertyId 유무에 따라 분기)
        Page<Review> reviewPage;
        FilterMetaDto filterMeta = null;

        if (requestDto.getPropertyId() != null && !requestDto.getPropertyId().isEmpty()) {
            // 특정 매물의 리뷰 조회 (모달용)
            reviewPage = reviewRepository.findByPropertyId(
                    requestDto.getPropertyId(),
                    pageable);

            // FilterMeta 생성
            filterMeta = createFilterMeta(requestDto.getPropertyId());

            log.info("특정 매물 리뷰 조회: propertyId={}, totalItems={}",
                    requestDto.getPropertyId(), reviewPage.getTotalElements());

        } else {
            // 전체 리뷰 조회 (게시판용)
            reviewPage = reviewRepository.findAll(pageable);

            log.info("전체 리뷰 조회: totalItems={}", reviewPage.getTotalElements());
        }

        // 3. DTO 변환
        List<ReviewSummaryDto> reviewSummaries = reviewPage.getContent().stream()
                .map(this::convertToSummaryDto)
                .collect(Collectors.toList());

        // 4. 페이징 정보 생성
        PaginationDto pagination = createPaginationDto(reviewPage, requestDto.getPage());

        // 5. 응답 생성
        return ReviewListResponseDto.builder()
                .filterMeta(filterMeta)
                .reviews(reviewSummaries)
                .pagination(pagination)
                .build();
    }

    /**
     * Pageable 객체 생성
     *
     * DTO의 page, size, sort 정보를 기반으로 Pageable 생성
     *
     * @param requestDto 리뷰 조회 요청 DTO
     * @return Pageable 객체
     */
    private Pageable createPageable(ReviewQueryRequestDto requestDto) {
        // 페이지 번호를 0-based index로 변환 (Spring Data는 0부터 시작)
        int pageIndex = requestDto.getPage() - 1;

        // 정렬 기준에 따른 Sort 객체 생성
        Sort sort = createSort(requestDto.getSort());

        return PageRequest.of(pageIndex, requestDto.getSize(), sort);
    }

    /**
     * 정렬 기준에 따른 Sort 객체 생성
     *
     * 설계 명세서: 6.3.1 Request Parameters - sort
     *
     * @param sortParam 정렬 파라미터 (latest, rating_desc, rating_asc)
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
            default -> Sort.by(Sort.Order.desc("createdAt")); // "latest"
        };
    }

    /**
     * FilterMeta 생성
     *
     * 특정 매물 조회 시, 해당 매물의 평균 평점 정보 조회
     *
     * @param propertyId 매물 ID
     * @return FilterMetaDto
     */
    private FilterMetaDto createFilterMeta(String propertyId) {
        // REVIEW_STATISTICS 테이블에서 평균 평점 조회
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
     * 설계 명세서: 6.3.2 응답 (Response) - reviews[]
     *
     * @param review Review 엔티티
     * @return ReviewSummaryDto
     */
    private ReviewSummaryDto convertToSummaryDto(Review review) {
        // 1. 사용자 ID 마스킹 처리 (예: "user1234" → "user****")
        String maskedUserId = maskUserId(review.getUserId());

        // 2. 내용 요약 (100자 이내)
        String summary = createSummary(review.getContent());

        // 3. 키워드 태그 추출
        List<String> tags = extractTags(review.getReviewId());

        // 4. 매물명 조회 (TODO: Property 테이블 조인 또는 별도 조회 필요)
        String propertyName = "매물명";  // 임시값

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
     * 예: "user1234" → "user****"
     *
     * @param userId 원본 사용자 ID
     * @return 마스킹된 사용자 ID
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 4) {
            return "****";
        }

        String prefix = userId.substring(0, 4);
        return prefix + "****";
    }

    /**
     * 리뷰 내용 요약 생성
     *
     * 100자 이내로 요약
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
     * 키워드 태그 추출
     *
     * REVIEW_KEYWORDS 테이블에서 해당 리뷰의 키워드 조회
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
     * 페이징 정보 DTO 생성
     *
     * Spring Data의 Page 객체를 PaginationDto로 변환
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