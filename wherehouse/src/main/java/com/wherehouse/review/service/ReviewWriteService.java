package com.wherehouse.review.service;

import com.wherehouse.review.component.KeywordExtractor;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 리뷰 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewWriteService {

    private final ReviewRepository reviewRepository;
    private final ReviewStatisticsRepository reviewStatisticsRepository;
    private final ReviewKeywordRepository reviewKeywordRepository;
    private final KeywordExtractor keywordExtractor;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 리뷰 작성
     *
     * 리뷰 저장 → 키워드 추출 → 통계 갱신 순서로 처리
     *
     * @param requestDto 리뷰 작성 요청 (propertyId, userId, rating, content)
     * @return 리뷰 작성 응답 (생성된 reviewId, createdAt)
     */
    @Transactional
    public ReviewCreateResponseDto createReview(ReviewCreateRequestDto requestDto) {

        // ======================================================================
        // Step 1: 중복 작성 방지 (한 사용자는 한 매물에 한 번만 리뷰 작성 가능)
        // ======================================================================
        if (reviewRepository.existsByPropertyIdAndUserId(
                requestDto.getPropertyId(),
                requestDto.getUserId())) {
            throw new IllegalStateException("이미 해당 매물에 대한 리뷰를 작성하셨습니다");
        }

        // ======================================================================
        // Step 2: 리뷰 엔티티 생성 및 저장 (REVIEWS 테이블)
        // ======================================================================
        Review review = Review.builder()
                .propertyId(requestDto.getPropertyId())
                .userId(requestDto.getUserId())
                .rating(requestDto.getRating())
                .content(requestDto.getContent())
                .build();

        Review savedReview = reviewRepository.save(review);
        log.info("리뷰 저장 완료: reviewId={}, propertyId={}",
                savedReview.getReviewId(), savedReview.getPropertyId());

        // ======================================================================
        // Step 3: 키워드 자동 추출 및 저장 (REVIEW_KEYWORDS 테이블)
        // ======================================================================
        List<ReviewKeyword> keywords = keywordExtractor.extractKeywords(
                savedReview.getReviewId(),
                requestDto.getContent()
        );

        if (!keywords.isEmpty()) {
            reviewKeywordRepository.saveAll(keywords);
            log.info("키워드 저장 완료: reviewId={}, keywordCount={}",
                    savedReview.getReviewId(), keywords.size());
        }

        // ======================================================================
        // Step 4: 리뷰 통계 초기화 또는 조회 (REVIEW_STATISTICS 테이블)
        // 해당 매물의 통계가 없으면 새로 생성, 있으면 기존 것 조회
        // ======================================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(requestDto.getPropertyId())
                .orElseGet(() -> {
                    ReviewStatistics newStats = ReviewStatistics.builder()
                            .propertyId(requestDto.getPropertyId())
                            .build();
                    return reviewStatisticsRepository.save(newStats);
                });

        // ======================================================================
        // Step 5: 리뷰 통계 재산출 (reviewCount, avgRating)
        // ======================================================================
        recalculateAndUpdateReviewStatistics(requestDto.getPropertyId(), statistics);

        // ======================================================================
        // Step 6: 키워드 통계 재산출 (positiveKeywordCount, negativeKeywordCount)
        // ======================================================================
        recalculateAndUpdateKeywordStatistics(requestDto.getPropertyId(), statistics);

        // ======================================================================
        // Step 7: 응답 생성
        // ======================================================================
        return ReviewCreateResponseDto.builder()
                .reviewId(savedReview.getReviewId())
                .createdAt(savedReview.getCreatedAt().format(ISO_FORMATTER))
                .build();
    }

    // ======================================================================
    // Private Methods
    // ======================================================================

    /**
     * 리뷰 통계 재산출 및 갱신
     *
     * 집계 쿼리로 reviewCount와 avgRating을 실시간 계산하여 통계 테이블 업데이트
     *
     * 호출 위치: createReview() - Step 5
     * 사용 목적: 리뷰 작성/수정/삭제 시 해당 매물의 리뷰 개수와 평균 별점을 재집계
     *
     * @param propertyId 매물 ID
     * @param statistics 리뷰 통계 엔티티 (업데이트 대상)
     */
    private void recalculateAndUpdateReviewStatistics(String propertyId, ReviewStatistics statistics) {

        // 1. 집계 쿼리 실행: COUNT(*), AVG(RATING)
        Object[] result = reviewRepository.aggregateReviewStats(propertyId);

        Long reviewCount = (Long) result[0];          // 리뷰 개수
        Double avgRatingDouble = (Double) result[1];  // 평균 별점

        // 2. 평균 별점 소수점 2자리로 반올림 (4.567 → 4.57)
        BigDecimal avgRating = BigDecimal.valueOf(avgRatingDouble)
                .setScale(2, RoundingMode.HALF_UP);

        // 3. 통계 엔티티 업데이트
        statistics.updateStatistics(reviewCount.intValue(), avgRating);

        log.info("리뷰 통계 갱신 완료: propertyId={}, reviewCount={}, avgRating={}",
                propertyId, reviewCount, avgRating);
    }

    /**
     * 키워드 통계 재산출 및 갱신
     *
     * 긍정/부정 키워드 개수를 집계하여 통계 테이블 업데이트
     *
     * 호출 위치: createReview() - Step 6
     * 사용 목적: 리뷰 작성 시 해당 매물의 모든 리뷰의 키워드를 집계하여
     *           긍정 키워드 수와 부정 키워드 수를 통계에 반영
     *
     * @param propertyId 매물 ID
     * @param statistics 리뷰 통계 엔티티 (업데이트 대상)
     */
    private void recalculateAndUpdateKeywordStatistics(String propertyId, ReviewStatistics statistics) {

        // 1. 집계 쿼리 실행: 긍정 키워드 수, 부정 키워드 수
        // SUM(CASE WHEN score > 0...), SUM(CASE WHEN score < 0...)
        Object[] result = reviewKeywordRepository.aggregateKeywordStats(propertyId);

        // 2. null 체크 (키워드가 없으면 null 반환)
        Long positiveCount = result[0] != null ? (Long) result[0] : 0L;
        Long negativeCount = result[1] != null ? (Long) result[1] : 0L;

        // 3. 통계 엔티티 업데이트
        statistics.updateKeywordStatistics(positiveCount.intValue(), negativeCount.intValue());

        log.info("키워드 통계 갱신 완료: propertyId={}, positiveCount={}, negativeCount={}",
                propertyId, positiveCount, negativeCount);
    }

    /**
     * 사용자 ID 마스킹 처리
     *
     * 개인정보 보호를 위해 앞 4자만 남기고 나머지는 *로 치환
     *
     * 호출 위치: convertToReviewDto() - ReviewDto 생성 시 userId 필드에 적용
     * 사용 목적: 리뷰 조회 API 응답에서 사용자 개인정보 노출 방지
     *
     * 예시:
     * - "user1234" → "user****"
     * - "kim" → "kim" (4자 이하는 그대로)
     * - null → null
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