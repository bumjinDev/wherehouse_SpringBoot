package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Entity
@Table(name = "REVIEW_KEYWORDS_CHARTER")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewKeywordCharter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_keyword_charter_id")
    @SequenceGenerator(name = "seq_keyword_charter_id", sequenceName = "SEQ_KEYWORD_CHARTER_ID", allocationSize = 1)
    @Column(name = "KEYWORD_ID", nullable = false)
    private Long keywordId;

    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    @Column(name = "KEYWORD", length = 50, nullable = false)
    private String keyword;

    @Column(name = "SCORE")
    private Integer score;

    @Builder
    public ReviewKeywordCharter(Long reviewId, String keyword, Integer score) {
        this.reviewId = reviewId;
        this.keyword = keyword;
        this.score = score;
    }
}
