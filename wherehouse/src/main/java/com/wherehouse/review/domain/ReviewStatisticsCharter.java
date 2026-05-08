package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "REVIEW_STATISTICS_CHARTER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewStatisticsCharter {

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
    public ReviewStatisticsCharter(String propertyId) {
        this.propertyId = propertyId;
        this.reviewCount = 0;
        this.avgRating = BigDecimal.ZERO;
        this.positiveKeywordCount = 0;
        this.negativeKeywordCount = 0;
        this.lastCalced = LocalDateTime.now();
    }

    public void updateStatistics(Integer reviewCount, BigDecimal avgRating) {
        this.reviewCount = reviewCount;
        this.avgRating = avgRating != null ? avgRating : BigDecimal.ZERO;
        this.lastCalced = LocalDateTime.now();
    }

    public void updateKeywordStatistics(Integer positiveCount, Integer negativeCount) {
        this.positiveKeywordCount = positiveCount != null ? positiveCount : 0;
        this.negativeKeywordCount = negativeCount != null ? negativeCount : 0;
        this.lastCalced = LocalDateTime.now();
    }

    public void updateAllStatistics(Integer reviewCount, BigDecimal avgRating,
                                    Integer positiveCount, Integer negativeCount) {
        this.reviewCount = reviewCount;
        this.avgRating = avgRating != null ? avgRating : BigDecimal.ZERO;
        this.positiveKeywordCount = positiveCount != null ? positiveCount : 0;
        this.negativeKeywordCount = negativeCount != null ? negativeCount : 0;
        this.lastCalced = LocalDateTime.now();
    }
}
