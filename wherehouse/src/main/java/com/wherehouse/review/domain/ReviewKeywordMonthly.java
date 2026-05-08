package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Entity
@Table(name = "REVIEW_KEYWORDS_MONTHLY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewKeywordMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_keyword_monthly_id")
    @SequenceGenerator(name = "seq_keyword_monthly_id", sequenceName = "SEQ_KEYWORD_MONTHLY_ID", allocationSize = 1)
    @Column(name = "KEYWORD_ID", nullable = false)
    private Long keywordId;

    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    @Column(name = "KEYWORD", length = 50, nullable = false)
    private String keyword;

    @Column(name = "SCORE")
    private Integer score;

    @Builder
    public ReviewKeywordMonthly(Long reviewId, String keyword, Integer score) {
        this.reviewId = reviewId;
        this.keyword = keyword;
        this.score = score;
    }
}
