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
    /**
     * 리뷰 통계 재산출 및 갱신
     *
     * ============================================================================
     * [1번째 작업] 점진적 갱신(Incremental Update) 로직 정립을 위한 성능 측정
     * ============================================================================
     *
     * 현재 로직 (V1_FULL_SCAN):
     *   - 리뷰 작성/수정/삭제 시마다 해당 매물의 모든 리뷰를 전수 스캔하여 COUNT, AVG 재계산
     *   - 시간 복잡도: O(N) - 리뷰 개수에 비례하여 처리 시간 증가
     *
     * 측정 목적:
     *   - 전수 집계 쿼리의 실제 소요 시간 실측
     *   - 데이터 건수(N)와 쿼리 시간의 상관관계 증명
     *   - 개선 후(V2_INCREMENTAL) 성능과 비교하기 위한 기준선(Baseline) 확보
     *
     * 측정 구간:
     *   - [QUERY]   : DB 집계 쿼리 실행 시간 (예상 병목 구간)
     *   - [COMPUTE] : 애플리케이션 레벨 연산 시간 (BigDecimal 반올림)
     *   - [UPDATE]  : 엔티티 메모리 갱신 시간 (실제 DB 반영은 트랜잭션 커밋 시)
     *   - [SUMMARY] : 전체 메서드 실행 시간 및 구간별 비율
     *
     * @param propertyId 매물 ID (MD5 해시 문자열)
     * @param statistics 리뷰 통계 엔티티 (업데이트 대상)
     */
    private void recalculateAndUpdateReviewStatistics(String propertyId, ReviewStatistics statistics) {

        // ==========================================================================
        // [로깅 분류 체계]
        // - TASK: 작업 순서 (TASK_1 = 1번째 작업: 점진적 갱신 로직 정립)
        // - VERSION: 로직 버전 (V1_FULL_SCAN = 개선 전 전수 집계 방식)
        // - 추후 V2_INCREMENTAL 적용 시 VERSION만 변경하여 성능 비교 가능
        // ==========================================================================
        final String TASK = "TASK_1";
        final String VERSION = "V1_FULL_SCAN";

        // ==========================================================================
        // [전체 메서드 시작 시점 기록]
        // - System.nanoTime(): 나노초 단위로 정밀 측정 (밀리초보다 1,000배 정밀)
        // - 빠른 연산 구간에서 0ms로 찍히는 문제 방지
        // ==========================================================================
        long methodStartNano = System.nanoTime();

        // ==========================================================================
        // [구간 1: 집계 쿼리 실행] - 예상 병목 구간
        //
        // 실행 쿼리:
        //   SELECT COUNT(r), COALESCE(AVG(r.rating), 0.0)
        //   FROM Review r
        //   WHERE r.propertyId = :propertyId
        //
        // 병목 원인:
        //   - 해당 매물의 모든 리뷰 행을 스캔하여 집계 (Full Scan)
        //   - 리뷰 N건 적재 시 → N개 행 읽기 발생 → O(N) 복잡도
        //   - 리뷰 10,000건일 때 vs 100건일 때 약 100배 시간 차이 예상
        // ==========================================================================
        long queryStartNano = System.nanoTime();

        List<Object[]> resultList = reviewRepository.aggregateReviewStats(propertyId);

        long queryElapsedNano = System.nanoTime() - queryStartNano;
        double queryElapsedMs = queryElapsedNano / 1_000_000.0;  // 나노초 → 밀리초 변환

        // ==========================================================================
        // [방어 로직: 데이터 없음]
        // - 집계 함수 특성상 보통 1행은 반환되나, null 또는 빈 결과 대비
        // - 해당 매물에 리뷰가 하나도 없는 초기 상태 처리
        // ==========================================================================
        if (resultList == null || resultList.isEmpty()) {
            statistics.updateStatistics(0, BigDecimal.ZERO);
            log.info("[{}][{}][QUERY] propertyId={}, result=EMPTY, queryTime={}ms",
                    TASK, VERSION, propertyId, String.format("%.2f", queryElapsedMs));
            return;
        }

        // ==========================================================================
        // [쿼리 결과 파싱]
        // - result[0]: COUNT(r) → 해당 매물의 총 리뷰 개수 (= 스캔된 행 수 = N)
        // - result[1]: AVG(r.rating) → 평균 별점 (소수점 이하 다수 자릿수)
        // ==========================================================================
        Object[] result = resultList.get(0);
        Long reviewCount = (Long) result[0];
        Double avgRatingDouble = (Double) result[1];

        // ==========================================================================
        // [구간 1 로그 출력]
        // - scannedRows: O(N) 증명의 핵심 데이터 (N값)
        // - rawAvg: DB에서 반환된 원시 평균값 (반올림 전)
        // - queryTime: 쿼리 실행 소요 시간 (병목 비중 측정용)
        // ==========================================================================
        log.info("[{}][{}][QUERY] propertyId={}, scannedRows={}, rawAvg={}, queryTime={}ms",
                TASK, VERSION, propertyId, reviewCount, avgRatingDouble,
                String.format("%.2f", queryElapsedMs));

        // ==========================================================================
        // [구간 2: 애플리케이션 레벨 연산]
        //
        // 수행 작업:
        //   - 평균 별점을 소수점 2자리로 반올림 (4.234567 → 4.23)
        //   - RoundingMode.HALF_UP: 사사오입 방식 (0.5 이상이면 올림)
        //
        // 예상 소요 시간:
        //   - 단순 수학 연산이므로 마이크로초~밀리초 이하 수준
        //   - 쿼리 대비 무시할 수 있는 수준으로 예상
        // ==========================================================================
        long computeStartNano = System.nanoTime();

        BigDecimal avgRating = BigDecimal.valueOf(avgRatingDouble)
                .setScale(2, RoundingMode.HALF_UP);

        long computeElapsedNano = System.nanoTime() - computeStartNano;
        double computeElapsedMs = computeElapsedNano / 1_000_000.0;

        log.info("[{}][{}][COMPUTE] propertyId={}, rawAvg={} -> roundedAvg={}, computeTime={}ms",
                TASK, VERSION, propertyId, avgRatingDouble, avgRating,
                String.format("%.2f", computeElapsedMs));

        // ==========================================================================
        // [구간 3: 통계 엔티티 갱신]
        //
        // 수행 작업:
        //   - ReviewStatistics 엔티티의 reviewCount, avgRating 필드 업데이트
        //   - 이 시점에서는 메모리상 객체만 변경됨
        //
        // 주의사항:
        //   - 실제 DB UPDATE 쿼리는 JPA Dirty Checking에 의해 트랜잭션 커밋 시 실행
        //   - 따라서 여기서 측정된 시간은 '메모리 갱신 시간'이며 'DB 반영 시간'이 아님
        // ==========================================================================
        long updateStartNano = System.nanoTime();

        statistics.updateStatistics(reviewCount.intValue(), avgRating);

        long updateElapsedNano = System.nanoTime() - updateStartNano;
        double updateElapsedMs = updateElapsedNano / 1_000_000.0;

        log.info("[{}][{}][UPDATE] propertyId={}, newCount={}, newAvg={}, updateTime={}ms (메모리 갱신, DB 반영은 트랜잭션 커밋 시)",
                TASK, VERSION, propertyId, reviewCount, avgRating,
                String.format("%.2f", updateElapsedMs));

        // ==========================================================================
        // [전체 메서드 종료 및 요약]
        // ==========================================================================
        long methodElapsedNano = System.nanoTime() - methodStartNano;
        double methodElapsedMs = methodElapsedNano / 1_000_000.0;

        // ==========================================================================
        // [구간별 비율 계산]
        // - 전체 시간 대비 각 구간이 차지하는 비중 산출
        // - 예상: QUERY 구간이 90% 이상 차지하여 병목임을 수치로 증명
        // ==========================================================================
        double queryRatio = (queryElapsedNano * 100.0) / methodElapsedNano;
        double computeRatio = (computeElapsedNano * 100.0) / methodElapsedNano;
        double updateRatio = (updateElapsedNano * 100.0) / methodElapsedNano;

        // ==========================================================================
        // [최종 요약 로그]
        // - N: 스캔된 리뷰 개수 (O(N) 복잡도 증명용)
        // - total: 전체 메서드 실행 시간
        // - query/compute/update: 각 구간별 시간 및 비율
        //
        // 활용 방법:
        //   1. N=100, N=1000, N=10000 등 다양한 데이터에서 테스트
        //   2. N 증가에 따른 query 시간의 선형 증가 패턴 확인
        //   3. V2_INCREMENTAL 적용 후 동일 조건에서 비교
        // ==========================================================================
        log.info("[{}][{}][SUMMARY] propertyId={}, N={}, finalAvg={}, " +
                        "total={}ms | query={}ms ({}) | compute={}ms ({}) | update={}ms ({})",
                TASK, VERSION, propertyId, reviewCount, avgRating,
                String.format("%.2f", methodElapsedMs),
                String.format("%.2f", queryElapsedMs), String.format("%.1f%%", queryRatio),
                String.format("%.2f", computeElapsedMs), String.format("%.1f%%", computeRatio),
                String.format("%.2f", updateElapsedMs), String.format("%.1f%%", updateRatio));
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