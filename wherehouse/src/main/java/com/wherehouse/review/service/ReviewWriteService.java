package com.wherehouse.review.service;

import com.wherehouse.review.component.KeywordExtractor;
import com.wherehouse.review.domain.*;
import com.wherehouse.review.dto.*;
import com.wherehouse.review.enums.StatisticsOperation;
import com.wherehouse.review.execptionHandler.ReviewAccessDeniedException;
import com.wherehouse.review.execptionHandler.ReviewDuplicateException;
import com.wherehouse.review.execptionHandler.ReviewNotFoundException;
import com.wherehouse.review.execptionHandler.ReviewXssException;
import com.wherehouse.review.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewWriteService {

    private final ReviewCharterRepository reviewCharterRepository;
    private final ReviewMonthlyRepository reviewMonthlyRepository;
    private final ReviewStatisticsCharterRepository reviewStatisticsCharterRepository;
    private final ReviewStatisticsMonthlyRepository reviewStatisticsMonthlyRepository;
    private final ReviewKeywordCharterRepository reviewKeywordCharterRepository;
    private final ReviewKeywordMonthlyRepository reviewKeywordMonthlyRepository;
    private final KeywordExtractor keywordExtractor;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String STATS_KEY_PREFIX = "review:stats:";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_SUM = "sum";
    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

    // ======================================================================
    // 캐시 초기화
    // ======================================================================

    @PostConstruct
    public void initializeStatisticsCache() {
        try {
            List<String[]> validEntries = new ArrayList<>();
            int skipCount = 0;

            for (ReviewStatisticsCharter stats : reviewStatisticsCharterRepository.findAll()) {
                String[] entry = toRedisEntry(stats.getPropertyId(), stats.getReviewCount(), stats.getAvgRating());
                if (entry != null) validEntries.add(entry);
                else skipCount++;
            }
            for (ReviewStatisticsMonthly stats : reviewStatisticsMonthlyRepository.findAll()) {
                String[] entry = toRedisEntry(stats.getPropertyId(), stats.getReviewCount(), stats.getAvgRating());
                if (entry != null) validEntries.add(entry);
                else skipCount++;
            }

            int successCount = flushToRedis(validEntries);

            log.info("[CACHE_INIT] 완료: 유효={}건, 스킵={}건, 성공={}건",
                    validEntries.size(), skipCount, successCount);
        } catch (Exception e) {
            log.error("[CACHE_INIT] 캐시 초기화 실패 — 서비스는 정상 기동: {}", e.getMessage());
        }
    }

    private String[] toRedisEntry(String propertyId, Integer reviewCount, BigDecimal avgRating) {
        if (reviewCount == null || reviewCount == 0) return null;
        if (avgRating == null) avgRating = BigDecimal.ZERO;

        long sum = avgRating
                .multiply(BigDecimal.valueOf(reviewCount))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        return new String[]{
                STATS_KEY_PREFIX + propertyId,
                String.valueOf(reviewCount),
                String.valueOf(sum)
        };
    }

    private int flushToRedis(List<String[]> entries) {
        int BATCH_SIZE = 5000;
        int successCount = 0;

        RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
        RedisSerializer hashValueSerializer = redisTemplate.getHashValueSerializer();

        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, entries.size());
            List<String[]> batch = entries.subList(i, end);

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
                log.error("[CACHE_INIT_ERROR] range={}-{}, error={}", i, end, e.getMessage());
            }
        }
        return successCount;
    }

    // ======================================================================
    // CREATE
    // ======================================================================

    @Transactional
    public ReviewCreateResponseDto createReview(ReviewCreateRequestDto requestDto, String userId) {

        String propertyId = requestDto.getPropertyId();
        boolean isCharter = "charter".equalsIgnoreCase(requestDto.getPropertyType());

        if (isCharter) {
            if (reviewCharterRepository.existsByPropertyIdAndUserId(propertyId, userId)) {
                throw new ReviewDuplicateException("이미 해당 매물에 대한 리뷰를 작성하셨습니다");
            }
        } else {
            if (reviewMonthlyRepository.existsByPropertyIdAndUserId(propertyId, userId)) {
                throw new ReviewDuplicateException("이미 해당 매물에 대한 리뷰를 작성하셨습니다");
            }
        }

        validateNoHtmlTags(requestDto.getContent());

        ReviewBase savedReview;
        if (isCharter) {
            savedReview = reviewCharterRepository.save(ReviewCharter.builder()
                    .propertyId(propertyId).userId(userId)
                    .rating(requestDto.getRating()).content(requestDto.getContent())
                    .build());
        } else {
            savedReview = reviewMonthlyRepository.save(ReviewMonthly.builder()
                    .propertyId(propertyId).userId(userId)
                    .rating(requestDto.getRating()).content(requestDto.getContent())
                    .build());
        }

        log.info("리뷰 저장 완료: reviewId={}, propertyId={}", savedReview.getReviewId(), propertyId);

        saveKeywords(savedReview.getReviewId(), requestDto.getContent(), isCharter);

        if (isCharter) {
            ReviewStatisticsCharter statistics = reviewStatisticsCharterRepository
                    .findById(propertyId)
                    .orElseGet(() -> reviewStatisticsCharterRepository.save(
                            ReviewStatisticsCharter.builder().propertyId(propertyId).build()));
            updateStatisticsIncremental(propertyId, statistics, StatisticsOperation.CREATE, null, requestDto.getRating());
            recalculateKeywordStatistics(propertyId, statistics, true);
        } else {
            ReviewStatisticsMonthly statistics = reviewStatisticsMonthlyRepository
                    .findById(propertyId)
                    .orElseGet(() -> reviewStatisticsMonthlyRepository.save(
                            ReviewStatisticsMonthly.builder().propertyId(propertyId).build()));
            updateStatisticsIncremental(propertyId, statistics, StatisticsOperation.CREATE, null, requestDto.getRating());
            recalculateKeywordStatistics(propertyId, statistics, false);
        }

        return ReviewCreateResponseDto.builder()
                .reviewId(savedReview.getReviewId())
                .createdAt(savedReview.getCreatedAt().format(ISO_FORMATTER))
                .build();
    }

    // ======================================================================
    // UPDATE
    // ======================================================================

    @Transactional
    public ReviewUpdateResponseDto updateReview(ReviewUpdateRequestDto requestDto, String userId) {

        boolean isCharter = "charter".equalsIgnoreCase(requestDto.getPropertyType());

        validateNoHtmlTags(requestDto.getContent());

        if (isCharter) {
            ReviewCharter review = reviewCharterRepository.findById(requestDto.getReviewId())
                    .orElseThrow(() -> new ReviewNotFoundException("리뷰를 찾을 수 없습니다: reviewId=" + requestDto.getReviewId()));
            if (!review.getUserId().equals(userId)) {
                throw new ReviewAccessDeniedException("본인이 작성한 리뷰만 수정할 수 있습니다");
            }

            Integer oldRating = review.getRating();
            String propertyId = review.getPropertyId();
            review.update(requestDto.getRating(), requestDto.getContent());
            reviewCharterRepository.save(review);

            reviewKeywordCharterRepository.deleteByReviewId(review.getReviewId());
            saveKeywords(review.getReviewId(), requestDto.getContent(), true);

            ReviewStatisticsCharter statistics = reviewStatisticsCharterRepository.findById(propertyId)
                    .orElseThrow(() -> new IllegalStateException("리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));
            updateStatisticsIncremental(propertyId, statistics, StatisticsOperation.UPDATE, oldRating, requestDto.getRating());
            recalculateKeywordStatistics(propertyId, statistics, true);

            return ReviewUpdateResponseDto.builder()
                    .reviewId(review.getReviewId())
                    .updatedAt(review.getUpdatedAt().format(ISO_FORMATTER))
                    .build();

        } else {
            ReviewMonthly review = reviewMonthlyRepository.findById(requestDto.getReviewId())
                    .orElseThrow(() -> new ReviewNotFoundException("리뷰를 찾을 수 없습니다: reviewId=" + requestDto.getReviewId()));
            if (!review.getUserId().equals(userId)) {
                throw new ReviewAccessDeniedException("본인이 작성한 리뷰만 수정할 수 있습니다");
            }

            Integer oldRating = review.getRating();
            String propertyId = review.getPropertyId();
            review.update(requestDto.getRating(), requestDto.getContent());
            reviewMonthlyRepository.save(review);

            reviewKeywordMonthlyRepository.deleteByReviewId(review.getReviewId());
            saveKeywords(review.getReviewId(), requestDto.getContent(), false);

            ReviewStatisticsMonthly statistics = reviewStatisticsMonthlyRepository.findById(propertyId)
                    .orElseThrow(() -> new IllegalStateException("리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));
            updateStatisticsIncremental(propertyId, statistics, StatisticsOperation.UPDATE, oldRating, requestDto.getRating());
            recalculateKeywordStatistics(propertyId, statistics, false);

            return ReviewUpdateResponseDto.builder()
                    .reviewId(review.getReviewId())
                    .updatedAt(review.getUpdatedAt().format(ISO_FORMATTER))
                    .build();
        }
    }

    // ======================================================================
    // DELETE
    // ======================================================================

    @Transactional
    public void deleteReview(Long reviewId, String propertyType, String userId) {

        boolean isCharter = "charter".equalsIgnoreCase(propertyType);

        if (isCharter) {
            ReviewCharter review = reviewCharterRepository.findById(reviewId)
                    .orElseThrow(() -> new ReviewNotFoundException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId));
            if (!review.getUserId().equals(userId)) {
                throw new ReviewAccessDeniedException("본인이 작성한 리뷰만 삭제할 수 있습니다");
            }

            String propertyId = review.getPropertyId();
            Integer deletedRating = review.getRating();

            reviewKeywordCharterRepository.deleteByReviewId(reviewId);
            reviewCharterRepository.delete(review);

            ReviewStatisticsCharter statistics = reviewStatisticsCharterRepository.findById(propertyId)
                    .orElseThrow(() -> new IllegalStateException("리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));
            updateStatisticsIncremental(propertyId, statistics, StatisticsOperation.DELETE, deletedRating, null);
            recalculateKeywordStatistics(propertyId, statistics, true);

        } else {
            ReviewMonthly review = reviewMonthlyRepository.findById(reviewId)
                    .orElseThrow(() -> new ReviewNotFoundException("리뷰를 찾을 수 없습니다: reviewId=" + reviewId));
            if (!review.getUserId().equals(userId)) {
                throw new ReviewAccessDeniedException("본인이 작성한 리뷰만 삭제할 수 있습니다");
            }

            String propertyId = review.getPropertyId();
            Integer deletedRating = review.getRating();

            reviewKeywordMonthlyRepository.deleteByReviewId(reviewId);
            reviewMonthlyRepository.delete(review);

            ReviewStatisticsMonthly statistics = reviewStatisticsMonthlyRepository.findById(propertyId)
                    .orElseThrow(() -> new IllegalStateException("리뷰 통계를 찾을 수 없습니다: propertyId=" + propertyId));
            updateStatisticsIncremental(propertyId, statistics, StatisticsOperation.DELETE, deletedRating, null);
            recalculateKeywordStatistics(propertyId, statistics, false);
        }

        log.info("리뷰 삭제 완료: reviewId={}", reviewId);
    }

    // ======================================================================
    // Private Helpers
    // ======================================================================

    private void validateNoHtmlTags(String content) {
        String sanitized = PLAIN_TEXT_POLICY.sanitize(content);
        if (!sanitized.equals(content)) {
            throw new ReviewXssException("리뷰 내용에 HTML 태그를 포함할 수 없습니다");
        }
    }

    private void saveKeywords(Long reviewId, String content, boolean isCharter) {
        List<ExtractedKeywordDto> keywords = keywordExtractor.extractKeywords(content);
        if (keywords.isEmpty()) return;

        if (isCharter) {
            List<ReviewKeywordCharter> entities = keywords.stream()
                    .map(k -> ReviewKeywordCharter.builder()
                            .reviewId(reviewId).keyword(k.getKeyword()).score(k.getScore()).build())
                    .collect(Collectors.toList());
            reviewKeywordCharterRepository.saveAll(entities);
        } else {
            List<ReviewKeywordMonthly> entities = keywords.stream()
                    .map(k -> ReviewKeywordMonthly.builder()
                            .reviewId(reviewId).keyword(k.getKeyword()).score(k.getScore()).build())
                    .collect(Collectors.toList());
            reviewKeywordMonthlyRepository.saveAll(entities);
        }

        log.info("키워드 저장 완료: reviewId={}, count={}", reviewId, keywords.size());
    }

    private void updateStatisticsIncremental(
            String propertyId, Object statistics, StatisticsOperation operation,
            Integer oldRating, Integer newRating) {

        String redisKey = STATS_KEY_PREFIX + propertyId;
        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();
        Map<String, Object> cached = hashOps.entries(redisKey);

        int currentCount = cached.isEmpty() ? 0 : ((Number) cached.get(FIELD_COUNT)).intValue();
        long currentSum = cached.isEmpty() ? 0L : ((Number) cached.get(FIELD_SUM)).longValue();

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

        BigDecimal avgRating = newCount > 0
                ? BigDecimal.valueOf(newSum).divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        if (newCount > 0) {
            hashOps.put(redisKey, FIELD_COUNT, newCount);
            hashOps.put(redisKey, FIELD_SUM, newSum);
        } else {
            redisTemplate.delete(redisKey);
        }

        if (statistics instanceof ReviewStatisticsCharter s) {
            s.updateStatistics(newCount, avgRating);
        } else if (statistics instanceof ReviewStatisticsMonthly s) {
            s.updateStatistics(newCount, avgRating);
        }
    }

    private void recalculateKeywordStatistics(String propertyId, Object statistics, boolean isCharter) {
        List<Object[]> result = isCharter
                ? reviewKeywordCharterRepository.aggregateKeywordStats(propertyId)
                : reviewKeywordMonthlyRepository.aggregateKeywordStats(propertyId);

        int positiveCount = 0;
        int negativeCount = 0;

        if (result != null && !result.isEmpty()) {
            Object[] row = result.get(0);
            positiveCount = row[0] != null ? ((Number) row[0]).intValue() : 0;
            negativeCount = row[1] != null ? ((Number) row[1]).intValue() : 0;
        }

        if (statistics instanceof ReviewStatisticsCharter s) {
            s.updateKeywordStatistics(positiveCount, negativeCount);
        } else if (statistics instanceof ReviewStatisticsMonthly s) {
            s.updateKeywordStatistics(positiveCount, negativeCount);
        }

        log.info("키워드 통계 갱신: propertyId={}, positive={}, negative={}", propertyId, positiveCount, negativeCount);
    }
}
