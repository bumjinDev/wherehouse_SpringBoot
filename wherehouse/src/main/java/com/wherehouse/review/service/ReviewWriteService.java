package com.wherehouse.review.service;

import com.wherehouse.review.component.KeywordExtractor;
import com.wherehouse.review.domain.Review;
import com.wherehouse.review.domain.ReviewKeyword;
import com.wherehouse.review.domain.ReviewStatistics;
import com.wherehouse.review.dto.*;
import com.wherehouse.review.enums.StatisticsOperation;
import com.wherehouse.review.repository.ReviewKeywordRepository;
import com.wherehouse.review.repository.ReviewRepository;
import com.wherehouse.review.repository.ReviewStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 리뷰 작성/수정/삭제 서비스 (V2_INCREMENTAL)
 *
 * ============================================================================
 * [버전 정보]
 * ============================================================================
 * V1_FULL_SCAN   : 매 요청마다 전수 집계 쿼리 실행 (O(N) 복잡도)
 * V2_INCREMENTAL : Redis 캐시 + 점진적 갱신 (O(1) 복잡도) ← 현재 버전
 *
 * [아키텍처 변경 사항]
 * ============================================================================
 * 1. @PostConstruct: 서비스 기동 시 RDB → Redis 캐시 워밍업
 * 2. Redis Hash 구조: review:stats:{propertyId} → {count, sum}
 * 3. 점진적 갱신: CREATE/UPDATE/DELETE 연산별 산술 공식 적용
 * 4. 이중 영속화: Redis(실시간) + RDB(영구 저장) 동기화
 *
 * [성능 개선 효과]
 * ============================================================================
 * - 시간 복잡도: O(N) → O(1)
 * - DB I/O: Full Table Scan → Index Unique Scan (1 row)
 * - 예상 개선율: N=10,000 기준 약 70x 성능 향상
 * ============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewWriteService {

    private final ReviewRepository reviewRepository;
    private final ReviewStatisticsRepository reviewStatisticsRepository;
    private final ReviewKeywordRepository reviewKeywordRepository;
    private final KeywordExtractor keywordExtractor;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ==========================================================================
    // [Redis 키 설계]
    // 형식: review:stats:{propertyId}
    // 필드: count (리뷰 개수), sum (별점 합계)
    // 설계 근거: avg 대신 sum 저장 → 갱신 시 단순 덧셈/뺄셈으로 O(1) 연산 가능
    // ==========================================================================
    private static final String STATS_KEY_PREFIX = "review:stats:";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_SUM = "sum";

    // ==========================================================================
    // [로깅 상수]
    // ==========================================================================
    private static final String TASK = "TASK_1";
    private static final String VERSION = "V2_INCREMENTAL";

    // ==========================================================================
    // [캐시 초기화] @PostConstruct
    // ==========================================================================
    /**
     * 서비스 빈 초기화 시 RDB의 통계 데이터를 Redis로 로드
     *
     * ========================================================================
     * [실행 시점]
     * Spring 컨테이너가 ReviewWriteService 빈을 생성한 직후 자동 호출
     *
     * [처리 로직]
     * 1. REVIEW_STATISTICS 테이블의 모든 행 조회
     * 2. 각 매물에 대해 sum = avgRating × reviewCount 역산
     * 3. Redis Hash에 count, sum 저장
     *
     * [설계 고려사항]
     * - 대량 데이터 시 Pipeline 적용 검토 필요 (현재는 단건 처리)
     * - 실패 시 서비스 기동 차단 여부 결정 필요
     * ========================================================================
     */
    @PostConstruct
    public void initializeStatisticsCache() {
        long startTime = System.nanoTime();

        // ==========================================================================
        // [Phase 1] RDB 전체 조회 + 유효 데이터 필터링 & sum 역산
        // ==========================================================================
        List<ReviewStatistics> allStats = reviewStatisticsRepository.findAll();

        // key → {reviewCount, sum} 매핑 (Redis 쓰기 대상만)
        List<String[]> validEntries = new ArrayList<>(); // [key, countStr, sumStr]
        int skipCount = 0;

        for (ReviewStatistics stats : allStats) {
            Integer reviewCount = stats.getReviewCount();
            BigDecimal avgRating = stats.getAvgRating();

            if (reviewCount == null || reviewCount == 0) {
                skipCount++;
                continue;
            }

            if (avgRating == null) {
                avgRating = BigDecimal.ZERO;
                log.warn("[{}][{}][INIT_WARN] propertyId={}, avgRating=null → 0으로 대체",
                        TASK, VERSION, stats.getPropertyId());
            }

            long sum = avgRating
                    .multiply(BigDecimal.valueOf(reviewCount))
                    .setScale(4, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            String key = STATS_KEY_PREFIX + stats.getPropertyId();
            validEntries.add(new String[]{key, String.valueOf(reviewCount), String.valueOf(sum)});
        }

        // ==========================================================================
        // [Phase 2] executePipelined 일괄 저장
        //
        // 변경 전: hashOps.put() × 2 × 52,020건 = 104,040 RTT (~69분 @40ms RTT)
        // 변경 후: hMSet × 5,000건/배치 = 1 RTT/배치 × 11배치 (~0.5초 @40ms RTT)
        // ==========================================================================
        int BATCH_SIZE = 5000;
        int successCount = 0;
        int failCount = 0;

        RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
        RedisSerializer hashValueSerializer = redisTemplate.getHashValueSerializer();

        for (int batchStart = 0; batchStart < validEntries.size(); batchStart += BATCH_SIZE) {

            int end = Math.min(batchStart + BATCH_SIZE, validEntries.size());
            List<String[]> batch = validEntries.subList(batchStart, end);

            try {
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {

                    for (String[] entry : batch) {
                        byte[] rawKey = keySerializer.serialize(entry[0]);

                        Map<byte[], byte[]> hash = new HashMap<>();
                        hash.put(keySerializer.serialize(FIELD_COUNT),
                                hashValueSerializer.serialize(Integer.parseInt(entry[1])));
                        hash.put(keySerializer.serialize(FIELD_SUM),
                                hashValueSerializer.serialize(Long.parseLong(entry[2])));

                        connection.hashCommands().hMSet(rawKey, hash);
                    }
                    return null;
                });

                successCount += batch.size();

            } catch (Exception e) {
                log.error("[{}][{}][INIT_BATCH_ERROR] range={}-{}, error={}",
                        TASK, VERSION, batchStart, end, e.getMessage());
                failCount += batch.size();
            }
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        log.info("[{}][{}][CACHE_INIT] 완료: 총={}건, 유효={}건, 스킵={}건, 성공={}건, 실패={}건, 소요시간={}ms",
                TASK, VERSION, allStats.size(), validEntries.size(), skipCount, successCount, failCount, elapsedMs);
    }

    // ==========================================================================
    // [리뷰 작성] CREATE
    // ==========================================================================
    /**
     * 리뷰 작성
     *
     * 리뷰 저장 → 키워드 추출 → 통계 갱신(V2_INCREMENTAL) 순서로 처리
     *
     * @param requestDto 리뷰 작성 요청 (propertyId, rating, content)
     * @param userId     작성자 ID
     * @return 리뷰 작성 응답 (생성된 reviewId, createdAt)
     */
    @Transactional
    public ReviewCreateResponseDto createReview(ReviewCreateRequestDto requestDto, String userId) {

        String propertyId = requestDto.getPropertyId();

        // ======================================================================
        // Step 1: 중복 작성 방지
        // ======================================================================
        if (reviewRepository.existsByPropertyIdAndUserId(propertyId, userId)) {
            throw new IllegalStateException("이미 해당 매물에 대한 리뷰를 작성하셨습니다");
        }

        // ======================================================================
        // Step 2: 리뷰 엔티티 생성 및 저장 (REVIEWS 테이블)
        // ======================================================================
        Review review = Review.builder()
                .propertyId(propertyId)
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
        // Step 4: 리뷰 통계 초기화 또는 조회
        // ======================================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElseGet(() -> {
                    ReviewStatistics newStats = ReviewStatistics.builder()
                            .propertyId(propertyId)
                            .build();
                    return reviewStatisticsRepository.save(newStats);
                });

        // ======================================================================
        // Step 5: [V2_INCREMENTAL] 점진적 통계 갱신
        // ======================================================================
        updateStatisticsIncremental(
                propertyId,
                statistics,
                StatisticsOperation.CREATE,
                null,                       // oldRating: CREATE 시 불필요
                requestDto.getRating()      // newRating: 새로 추가되는 별점
        );

        // ======================================================================
        // Step 6: 키워드 통계 재산출 (기존 로직 유지)
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

    // ==========================================================================
    // [리뷰 수정] UPDATE
    // ==========================================================================
    /**
     * 리뷰 수정
     *
     * @param requestDto 리뷰 수정 요청 (reviewId, rating, content)
     * @return 리뷰 수정 응답 (reviewId, updatedAt)
     */
    @Transactional
    public ReviewUpdateResponseDto updateReview(ReviewUpdateRequestDto requestDto, String userId) {

        // ======================================================================
        // Step 1: 리뷰 조회
        // ======================================================================
        Review review = reviewRepository.findById(requestDto.getReviewId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + requestDto.getReviewId()));

        // ======================================================================
        // Step 1-1: 본인 작성 리뷰 여부 검증 (인가 처리)
        // ======================================================================
        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("본인이 작성한 리뷰만 수정할 수 있습니다");
        }

        String propertyId = review.getPropertyId();
        Integer oldRating = review.getRating();  // 수정 전 별점 (점진적 갱신에 필요)

        log.info("리뷰 수정 시작: reviewId={}, propertyId={}, oldRating={}, newRating={}",
                review.getReviewId(), propertyId, oldRating, requestDto.getRating());

        // ======================================================================
        // Step 2: 리뷰 엔티티 업데이트
        // ======================================================================
        review.update(requestDto.getRating(), requestDto.getContent());
        Review updatedReview = reviewRepository.save(review);

        log.info("리뷰 엔티티 수정 완료: reviewId={}", updatedReview.getReviewId());

        // ======================================================================
        // Step 3: 기존 키워드 삭제
        // ======================================================================
        reviewKeywordRepository.deleteByReviewId(review.getReviewId());
        log.info("기존 키워드 삭제 완료: reviewId={}", review.getReviewId());

        // ======================================================================
        // Step 4: 새로운 키워드 추출 및 저장
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
        // Step 5: 리뷰 통계 조회
        // ======================================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElseThrow(() -> new IllegalStateException(
                        "리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));

        // ======================================================================
        // Step 6: [V2_INCREMENTAL] 점진적 통계 갱신
        // ======================================================================
        updateStatisticsIncremental(
                propertyId,
                statistics,
                StatisticsOperation.UPDATE,
                oldRating,                  // oldRating: 수정 전 별점
                requestDto.getRating()      // newRating: 수정 후 별점
        );

        // ======================================================================
        // Step 7: 키워드 통계 재산출
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

    // ==========================================================================
    // [리뷰 삭제] DELETE
    // ==========================================================================
    /**
     * 리뷰 삭제
     *
     * @param reviewId 삭제할 리뷰 ID
     */
    @Transactional
    public void deleteReview(Long reviewId, String userId) {

        // Step 1: 리뷰 조회 =======================================================
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "리뷰를 찾을 수 없습니다: reviewId=" + reviewId));

        // Step 1-1: 본인 작성 리뷰 여부 검증 (인가 처리) ===========================
        if (!review.getUserId().equals(userId)) {
            throw new SecurityException("본인이 작성한 리뷰만 삭제할 수 있습니다");
        }

        String propertyId = review.getPropertyId();
        Integer deletedRating = review.getRating();  // 삭제되는 별점

        log.info("리뷰 삭제 시작: reviewId={}, propertyId={}, rating={}",
                review.getReviewId(), propertyId, deletedRating);
        // Step 2: 키워드 삭제 =====================================================
        reviewKeywordRepository.deleteByReviewId(reviewId);
        log.info("리뷰 키워드 삭제 완료: reviewId={}", reviewId);
        // Step 3: 리뷰 삭제 ======================================================
        reviewRepository.delete(review);
        log.info("리뷰 삭제 완료: reviewId={}", reviewId);
        // Step 4: 리뷰 통계 조회 =================================================
        ReviewStatistics statistics = reviewStatisticsRepository
                .findById(propertyId)
                .orElseThrow(() -> new IllegalStateException(
                        "리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));
        // Step 5: 통계 갱신 ======================================================
        updateStatisticsIncremental(
                propertyId,
                statistics,
                StatisticsOperation.DELETE,
                deletedRating,              // oldRating: 삭제되는 별점
                null                        // newRating: DELETE 시 불필요
        );
        // Step 6: 키워드 통계 재산출 ===============================================
        recalculateAndUpdateKeywordStatistics(propertyId, statistics);

        log.info("리뷰 삭제 및 통계 갱신 완료: propertyId={}, reviewId={}", propertyId, reviewId);
    }

    // ==========================================================================
    // [핵심 메서드] 점진적 통계 갱신 (V2_INCREMENTAL)
    // ==========================================================================
    /**
     * 통계 점진적 갱신 (V2_INCREMENTAL)
     *
     * @param propertyId 매물 ID
     * @param statistics 리뷰 통계 엔티티
     * @param operation  연산 유형 (CREATE, UPDATE, DELETE)
     * @param oldRating  기존 별점 (UPDATE: 수정 전, DELETE: 삭제 대상)
     * @param newRating  새 별점 (CREATE: 신규, UPDATE: 수정 후)
     */
    private void updateStatisticsIncremental(
            String propertyId,
            ReviewStatistics statistics,
            StatisticsOperation operation,
            Integer oldRating,
            Integer newRating) {

        String redisKey = STATS_KEY_PREFIX + propertyId;
        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();
        // 1. Redis에서 기존 count, sum 조회
        Map<String, Object> cached = hashOps.entries(redisKey);

        int currentCount;
        long currentSum;

        if (cached.isEmpty()) {
            currentCount = 0;
            currentSum = 0L;
        } else {
            currentCount = ((Number) cached.get(FIELD_COUNT)).intValue();
            currentSum = ((Number) cached.get(FIELD_SUM)).longValue();
        }
        // 2. 점진적 갱신 연산
        int newCount = currentCount;
        long newSum = currentSum;

        switch (operation) {
            case CREATE:
                newCount = currentCount + 1;
                newSum = currentSum + newRating;
                break;
            case UPDATE:
                newSum = currentSum - oldRating + newRating;
                break;
            case DELETE:
                newCount = currentCount - 1;
                newSum = currentSum - oldRating;
                break;
        }
        // 3. 평균 계산
        BigDecimal avgRating = newCount > 0
                ? BigDecimal.valueOf(newSum)
                .divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        // 4. Redis 갱신
        if (newCount > 0) {
            hashOps.put(redisKey, FIELD_COUNT, newCount);
            hashOps.put(redisKey, FIELD_SUM, newSum);
        } else {
            redisTemplate.delete(redisKey);
        }
        // 5. RDB 통계 테이블 갱신
        statistics.updateStatistics(newCount, avgRating);
    }

    // ==========================================================================
    // [키워드 통계] 기존 로직 유지 (2.1.6 병목은 별도 작업)
    // ==========================================================================
    /**
     * 키워드 통계 재산출 및 갱신
     *
     * 긍정/부정 키워드 개수를 집계하여 통계 테이블 업데이트
     * (이 메서드는 2.1.6 병목 해결 시 별도 개선 예정)
     *
     * @param propertyId 매물 ID
     * @param statistics 리뷰 통계 엔티티 (업데이트 대상)
     */
    private void recalculateAndUpdateKeywordStatistics(String propertyId, ReviewStatistics statistics) {

        List<Object[]> result = reviewKeywordRepository.aggregateKeywordStats(propertyId);

        if (result == null || result.isEmpty()) {
            statistics.updateKeywordStatistics(0, 0);
            return;
        }

        Object[] resultList = result.get(0);

        Long positiveCount = (resultList[0] != null) ? (Long) resultList[0] : 0L;
        Long negativeCount = (resultList[1] != null) ? (Long) resultList[1] : 0L;

        statistics.updateKeywordStatistics(positiveCount.intValue(), negativeCount.intValue());

        log.info("키워드 통계 갱신 완료: propertyId={}, positiveCount={}, negativeCount={}",
                propertyId, positiveCount, negativeCount);
    }
}
