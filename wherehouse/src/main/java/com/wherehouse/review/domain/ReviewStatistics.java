package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 리뷰 통계 엔티티
 *
 * 용도: 실시간 집계 데이터
 * Redis 매핑: stats:charter:{id}, stats:monthly:{id}
 *
 * 설계 명세서: 7.1.2 B. REVIEW_STATISTICS (리뷰 통계)
 */
@Entity
@Table(name = "REVIEW_STATISTICS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewStatistics {

    /**
     * 매물 ID (1:1 관계)
     * FK 제약조건 없음 - 전세/월세 테이블 모두 참조 가능
     */
    @Id
    @Column(name = "PROPERTY_ID", length = 32, nullable = false)
    private String propertyId;

    @Column(name = "REVIEW_COUNT", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "AVG_RATING", precision = 3, scale = 2, nullable = false)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "POSITIVE_KEYWORD_COUNT", nullable = false)
    private Integer positiveKeywordCount = 0;

    @Column(name = "NEGATIVE_KEYWORD_COUNT", nullable = false)
    private Integer negativeKeywordCount = 0;

    @Column(name = "LAST_CALCED")
    private LocalDateTime lastCalced;

    @Builder
    public ReviewStatistics(String propertyId) {
        this.propertyId = propertyId;
        this.reviewCount = 0;
        this.avgRating = BigDecimal.ZERO;
        this.positiveKeywordCount = 0;
        this.negativeKeywordCount = 0;
        this.lastCalced = LocalDateTime.now();
    }

    /**
     * 리뷰 통계 갱신
     *
     * 설계 명세서: 8.4.2 통합 재산출 프로세스
     */
    public void updateStatistics(Integer reviewCount, BigDecimal avgRating) {
        this.reviewCount = reviewCount;
        this.avgRating = avgRating != null ? avgRating : BigDecimal.ZERO;
        this.lastCalced = LocalDateTime.now();
    }

    /**
     * 키워드 통계 갱신
     *
     * 설계 명세서: 8.4.3 키워드 통계 재산출 로직
     */
    public void updateKeywordStatistics(Integer positiveCount, Integer negativeCount) {
        this.positiveKeywordCount = positiveCount != null ? positiveCount : 0;
        this.negativeKeywordCount = negativeCount != null ? negativeCount : 0;
        this.lastCalced = LocalDateTime.now();
    }

    /**
     * 전체 통계 일괄 갱신
     */
    public void updateAllStatistics(Integer reviewCount, BigDecimal avgRating,
                                    Integer positiveCount, Integer negativeCount) {
        this.reviewCount = reviewCount;
        this.avgRating = avgRating != null ? avgRating : BigDecimal.ZERO;
        this.positiveKeywordCount = positiveCount != null ? positiveCount : 0;
        this.negativeKeywordCount = negativeCount != null ? negativeCount : 0;
        this.lastCalced = LocalDateTime.now();
    }
}