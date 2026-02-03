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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 리뷰 작성/수정 서비스
 *
 * 설계 명세서: 6.2 리뷰 작성 API, 6.5 리뷰 수정 API
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
    /**
     * 리뷰 작성
     *
     * 리뷰 저장 → 키워드 추출 → 통계 갱신 순서로 처리
     * [수정] propertyId(String) 기반 로직으로 전면 수정
     *
     * @param requestDto 리뷰 작성 요청 (propertyId, rating, content)
     * @param userId     작성자 ID
     * @return 리뷰 작성 응답 (생성된 reviewId, createdAt)
     */
    @Transactional
    public ReviewCreateResponseDto createReview(ReviewCreateRequestDto requestDto, String userId) {

        // [수정] DTO에서 propertyId 추출 (MD5 String)
        String propertyId = requestDto.getPropertyId();

        // ======================================================================
        // Step 1: 중복 작성 방지 (한 사용자는 한 매물에 한 번만 리뷰 작성 가능)
        // [수정] propertyName이 아닌 propertyId로 조회해야 함
        // ======================================================================
        if (reviewRepository.existsByPropertyIdAndUserId(propertyId, userId)) {
            throw new IllegalStateException("이미 해당 매물에 대한 리뷰를 작성하셨습니다");
        }

        // ======================================================================
        // Step 2: 리뷰 엔티티 생성 및 저장 (REVIEWS 테이블)
        // ======================================================================
        Review review = Review.builder()
                .propertyId(propertyId)      // [수정] 매물 ID 저장
                .userId(userId)
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
                .findById(propertyId)
                .orElseGet(() -> {
                    ReviewStatistics newStats = ReviewStatistics.builder()
                            .propertyId(propertyId) // String ID
                            .build();
                    return reviewStatisticsRepository.save(newStats);
                });

        // ======================================================================
        // Step 5: 리뷰 통계 재산출 (reviewCount, avgRating)
        // ======================================================================
        recalculateAndUpdateReviewStatistics(propertyId, statistics);

        // ======================================================================
        // Step 6: 키워드 통계 재산출 (positiveKeywordCount, negativeKeywordCount)
        // ======================================================================
        recalculateAndUpdateKeywordStatistics(propertyId, statistics);

        // ======================================================================
        // Step 7: 응답 생성
        // ======================================================================
        return ReviewCreateResponseDto.builder()
                .reviewId(savedReview.getReviewId())
                .createdAt(savedReview.getCreatedAt().format(ISO_FORMATTER))
                .build();
    }

    /**
     * 리뷰 수정
     *
     * 설계 명세서: 6.5 리뷰 수정 API
     *
     * 리뷰 수정 → 기존 키워드 삭제 → 새 키워드 추출 → 통계 재산출 순서로 처리
     *
     * @param requestDto 리뷰 수정 요청 (reviewId, rating, content)
     * @return 리뷰 수정 응답 (reviewId, updatedAt)
     */
    @Transactional
    public ReviewUpdateResponseDto updateReview(ReviewUpdateRequestDto requestDto) {

        // ======================================================================
        // Step 1: 리뷰 조회 (존재하지 않으면 예외 발생)
        // ======================================================================
        Review review = reviewRepository.findById(requestDto.getReviewId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + requestDto.getReviewId()));

        String propertyId = review.getPropertyId();

        log.info("리뷰 수정 시작: reviewId={}, propertyId={}, oldRating={}, newRating={}",
                review.getReviewId(), propertyId, review.getRating(), requestDto.getRating());

        // ======================================================================
        // Step 2: 리뷰 엔티티 업데이트 (REVIEWS 테이블)
        // ======================================================================
        review.update(requestDto.getRating(), requestDto.getContent());

        // 즉시 DB에 반영 (통계 재산출 전에 변경사항이 DB에 반영되어야 함)
        Review updatedReview = reviewRepository.save(review);

        log.info("리뷰 엔티티 수정 완료: reviewId={}", updatedReview.getReviewId());

        // ======================================================================
        // Step 3: 기존 키워드 삭제 (REVIEW_KEYWORDS 테이블)
        // ======================================================================
        reviewKeywordRepository.deleteByReviewId(review.getReviewId());
        log.info("기존 키워드 삭제 완료: reviewId={}", review.getReviewId());

        // ======================================================================
        // Step 4: 새로운 키워드 자동 추출 및 저장 (REVIEW_KEYWORDS 테이블)
        // ======================================================================
        List<ReviewKeyword> newKeywords = keywordExtractor.extractKeywords(
                review.getReviewId(),
                requestDto.getContent()
        );

        if (!newKeywords.isEmpty()) {
            reviewKeywordRepository.saveAll(newKeywords);
            log.info("새 키워드 저장 완료: reviewId={}, keywordCount={}",
                    review.getReviewId(), newKeywords.size());
        }

        // ======================================================================
        // Step 5: 리뷰 통계 조회 (REVIEW_STATISTICS 테이블)
        // ======================================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElseThrow(() -> new IllegalStateException(
                        "리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));

        // ======================================================================
        // Step 6: 리뷰 통계 재산출 (reviewCount, avgRating)
        // ======================================================================
        recalculateAndUpdateReviewStatistics(propertyId, statistics);

        // ======================================================================
        // Step 7: 키워드 통계 재산출 (positiveKeywordCount, negativeKeywordCount)
        // ======================================================================
        recalculateAndUpdateKeywordStatistics(propertyId, statistics);

        // ======================================================================
        // Step 8: 응답 생성
        // ======================================================================
        return ReviewUpdateResponseDto.builder()
                .reviewId(review.getReviewId())
                .updatedAt(review.getUpdatedAt().format(ISO_FORMATTER))
                .build();
    }


    // ========

    /**
     * 리뷰 수정 == 추후 Spring Secuiry FilterChain 반영 시 실제로 활성화 해야 되는 리뷰 수정
     *
     * 설계 명세서: 6.5 리뷰 수정 API
     *
     * 리뷰 수정 → 작성자 검증 → 기존 키워드 삭제 → 새 키워드 추출 → 통계 재산출
     *
     * @param requestDto 리뷰 수정 요청 (reviewId, rating, content)
     * @param userId     현재 로그인한 사용자 ID (작성자 검증용)
     * @return 리뷰 수정 응답 (reviewId, updatedAt)
   
    @Transactional
    public ReviewUpdateResponseDto updateReview(ReviewUpdateRequestDto requestDto, String userId) {

        // ======================================================================
        // Step 1: 리뷰 조회 (존재하지 않으면 예외 발생)
        // ======================================================================
        Review review = reviewRepository.findById(requestDto.getReviewId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + requestDto.getReviewId()));

        // ======================================================================
        // Step 2: 작성자 권한 검증 (Security Verification)
        // 요청자와 원본 작성자가 다를 경우 수정 거부
        // ======================================================================
        if (!review.getUserId().equals(userId)) {
            log.error("리뷰 수정 권한 없음: reviewId={}, ownerId={}, requesterId={}",
                    review.getReviewId(), review.getUserId(), userId);
            throw new IllegalStateException("본인의 리뷰만 수정할 수 있습니다.");
        }

        String propertyId = review.getPropertyId();

        log.info("리뷰 수정 시작: reviewId={}, propertyId={}, oldRating={}, newRating={}",
                review.getReviewId(), propertyId, review.getRating(), requestDto.getRating());

        // ======================================================================
        // Step 3: 리뷰 엔티티 업데이트 (REVIEWS 테이블)
        // ======================================================================
        review.update(requestDto.getRating(), requestDto.getContent());

        // 즉시 DB에 반영 (통계 재산출 전에 변경사항이 DB에 반영되어야 함)
        Review updatedReview = reviewRepository.save(review);

        log.info("리뷰 엔티티 수정 완료: reviewId={}", updatedReview.getReviewId());

        // ======================================================================
        // Step 4: 기존 키워드 삭제 (REVIEW_KEYWORDS 테이블)
        // ======================================================================
        reviewKeywordRepository.deleteByReviewId(review.getReviewId());
        log.info("기존 키워드 삭제 완료: reviewId={}", review.getReviewId());

        // ======================================================================
        // Step 5: 새로운 키워드 자동 추출 및 저장 (REVIEW_KEYWORDS 테이블)
        // ======================================================================
        List<ReviewKeyword> newKeywords = keywordExtractor.extractKeywords(
                review.getReviewId(),
                requestDto.getContent()
        );

        if (!newKeywords.isEmpty()) {
            reviewKeywordRepository.saveAll(newKeywords);
            log.info("새 키워드 저장 완료: reviewId={}, keywordCount={}",
                    review.getReviewId(), newKeywords.size());
        }

        // ======================================================================
        // Step 6: 리뷰 통계 조회 (REVIEW_STATISTICS 테이블)
        // ======================================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElseThrow(() -> new IllegalStateException(
                        "리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));

        // ======================================================================
        // Step 7: 리뷰 통계 재산출 (reviewCount, avgRating)
        // ======================================================================
        recalculateAndUpdateReviewStatistics(propertyId, statistics);

        // ======================================================================
        // Step 8: 키워드 통계 재산출 (positiveKeywordCount, negativeKeywordCount)
        // ======================================================================
        recalculateAndUpdateKeywordStatistics(propertyId, statistics);

        // ======================================================================
        // Step 9: 응답 생성
        // ======================================================================
        return ReviewUpdateResponseDto.builder()
                .reviewId(review.getReviewId())
                .updatedAt(review.getUpdatedAt().format(ISO_FORMATTER))
                .build();
    }

    */

    // ========

    /**
     * 리뷰 삭제
     *
     * 설계 명세서: 6.6 리뷰 삭제 API
     * 리뷰 삭제 → 키워드 삭제 (CASCADE) → 통계 재산출 순서로 처리
     *
     * [트랜잭션 설정 근거]
     * 1. JPA 정책: deleteByReviewId와 같은 파생 쿼리(Derived Query)는 기본적으로 트랜잭션을 내장하지 않으므로,
     * 외부 트랜잭션이 없으면 jakarta.persistence.TransactionRequiredException 발생함.
     * 2. 데이터 무결성: '키워드 삭제' -> '리뷰 삭제' -> '통계 갱신'의 3단계 프로세스가
     * 모두 성공하거나 모두 실패(Rollback)해야 하는 원자성(Atomicity)을 보장해야 함.
     *
     * @param reviewId 리뷰 ID
     */
    @Transactional
    public void deleteReview(Long reviewId) {

        // ======================================================================
        // Step 1: 리뷰 조회 (존재하지 않으면 예외 발생)
        // ======================================================================
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + reviewId));

        String propertyId = review.getPropertyId();

        log.info("리뷰 삭제 시작: reviewId={}, propertyId={}, rating={}",
                review.getReviewId(), propertyId, review.getRating());

        // ======================================================================
        // Step 2: 키워드 삭제 (REVIEW_KEYWORDS 테이블)
        // FK 제약조건 ON DELETE CASCADE가 있다면 자동 삭제되지만, 명시적으로 삭제
        // ======================================================================
        reviewKeywordRepository.deleteByReviewId(reviewId);
        log.info("리뷰 키워드 삭제 완료: reviewId={}", reviewId);

        // ======================================================================
        // Step 3: 리뷰 삭제 (REVIEWS 테이블)
        // ======================================================================
        reviewRepository.delete(review);
        log.info("리뷰 삭제 완료: reviewId={}", reviewId);

        // ======================================================================
        // Step 4: 리뷰 통계 조회 (REVIEW_STATISTICS 테이블)
        // ======================================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElseThrow(() -> new IllegalStateException(
                        "리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));

        // ======================================================================
        // Step 5: 리뷰 통계 재산출 (reviewCount, avgRating)
        // ======================================================================
        recalculateAndUpdateReviewStatistics(propertyId, statistics);

        // ======================================================================
        // Step 6: 키워드 통계 재산출 (positiveKeywordCount, negativeKeywordCount)
        // ======================================================================
        recalculateAndUpdateKeywordStatistics(propertyId, statistics);

        log.info("리뷰 삭제 및 통계 갱신 완료: propertyId={}, reviewId={}", propertyId, reviewId);
    }

    // ======================================================================
    // Private Methods
    // ======================================================================

    /**
     * 리뷰 통계 재산출 및 갱신
     *
     * 집계 쿼리로 reviewCount와 avgRating을 실시간 계산하여 통계 테이블 업데이트
     *
     * 호출 위치: createReview() - Step 5, updateReview() - Step 6
     * 사용 목적: 리뷰 작성/수정/삭제 시 해당 매물의 리뷰 개수와 평균 별점을 재집계
     *
     * @param propertyId 매물 ID
     * @param statistics 리뷰 통계 엔티티 (업데이트 대상)
     */
    private void recalculateAndUpdateReviewStatistics(String propertyId, ReviewStatistics statistics) {

        // 1. 집계 쿼리 실행: List<Object[]> 반환
        List<Object[]> resultList = reviewRepository.aggregateReviewStats(propertyId);

        // 데이터가 없는 경우(혹은 null) 방어 로직 (집계 함수라 보통 1줄은 나오지만 안전하게 처리)
        if (resultList == null || resultList.isEmpty()) {
            statistics.updateStatistics(0, BigDecimal.ZERO);
            return;
        }

        // 첫 번째 행(Row)을 가져옴
        Object[] result = resultList.get(0);

        // 2. 데이터 파싱
        Long reviewCount = (Long) result[0];          // COUNT(r)
        Double avgRatingDouble = (Double) result[1];  // AVG(r.rating)

        log.info("매물 {} 의 리뷰 평균 별점 및 리뷰 개수 집계 산출 완료, reviewCount={}, avgRating={} ",
                propertyId, reviewCount, avgRatingDouble);

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
     * 호출 위치: createReview() - Step 6, updateReview() - Step 7
     * 사용 목적: 리뷰 작성/수정 시 해당 매물의 모든 리뷰의 키워드를 집계하여
     *           긍정 키워드 수와 부정 키워드 수를 통계에 반영
     *
     * @param propertyId 매물 ID
     * @param statistics 리뷰 통계 엔티티 (업데이트 대상)
     */
    private void recalculateAndUpdateKeywordStatistics(String propertyId, ReviewStatistics statistics) {

        // 1. 집계 쿼리 실행: 긍정 키워드 수, 부정 키워드 수
        // SUM(CASE WHEN score > 0...), SUM(CASE WHEN score < 0...)
        List<Object[]> result = reviewKeywordRepository.aggregateKeywordStats(propertyId);

        // 데이터가 없는 경우(혹은 null) 방어 로직 (집계 함수라 보통 1줄은 나오지만 안전하게 처리)
        if (result == null || result.isEmpty()) {
            statistics.updateStatistics(0, BigDecimal.ZERO);
            return;
        }

        // 첫 번째 행(Row)을 가져옴
        Object[] resultList = result.get(0);

        // 2. 데이터 파싱 (Null일 경우 0L로 대체)
        Long positiveCount = (resultList[0] != null) ? (Long) resultList[0] : 0L;
        Long negativeCount = (resultList[1] != null) ? (Long) resultList[1] : 0L;

        System.out.println("값 검사");
        System.out.println("positiveCount : " + positiveCount);
        System.out.println("negativeCount : " + negativeCount);

// 3. 통계 엔티티 업데이트
        statistics.updateKeywordStatistics(positiveCount.intValue(), negativeCount.intValue());

        System.out.println("값 검사");
        System.out.println("(Long) resultList[0] : " + positiveCount);
        System.out.println("(Long) resultList[1] : " + negativeCount);

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